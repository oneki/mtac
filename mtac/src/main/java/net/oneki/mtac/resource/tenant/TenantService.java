package net.oneki.mtac.resource.tenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import net.oneki.mtac.framework.cache.PgNotifierService;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.security.RoleUserInfo;
import net.oneki.mtac.model.core.security.TenantRole;
import net.oneki.mtac.model.core.security.TenantUserInfo;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.RoleRepository;
import net.oneki.mtac.resource.iam.identity.group.DefaultGroupService;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupRepository;

@Service
public abstract class TenantService<U extends UpsertRequest, E extends Tenant> extends ResourceService<U, E> {
    @Value("${mtac.iam.tenants-in-idtoken.enabled:false}")
    private boolean storeTenantsInIdToken;

    @Value("${mtac.iam.tenants-in-idtoken.schema-labels:}")
    private Set<String> storeTenantsSchemaLabels;

    @Autowired
    protected ResourceRepository resourceRepository;

    @Autowired
    protected DefaultGroupService groupService;

    @Autowired
    protected PgNotifierService pgNotifierService;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected GroupMembershipRepository groupMembershipRepository;

    @Autowired
    protected GroupRepository groupRepository;

    @Override
    public void delete(E tenant) {
        if (tenant == null) {
            return;
        }
        // Verify if the user has the permission to delete the resource
        if (!permissionService.hasPermission(tenant.getId(), "delete")) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }

        var resources = resourceRepository.listByTenantUnsecure(tenant.getId(), Query.builder()
                .forceTenant(true) // we don't want to retrieve resource with pub=true
                .noPublic(true)
                .build());
        var links = resources.stream()
                .filter(r -> r.isLink())
                .map(r -> r.getLinkId())
                .collect(Collectors.toList());

        if (resources.size() > links.size()) {
            throw new BusinessException("BSN_TENANT_NOT_EMPTY",
                    "Can't delete tenant with id:" + tenant.getId() + " because it's not empty");
        }

        // unlink resources
        deleteByIdsUnsecure(links);

        resourceRepository.delete(tenant.getId());
    }

    public record TenantClaims(List<Integer> tenantSids, List<Integer> sids,
            Map<String, RoleUserInfo> roles, TenantUserInfo tenants,
            Set<String> tenantUids) {
        public Map<String, Object> asClaims() {
            var claims = new HashMap<String, Object>();
            claims.put("tenantSids", tenantSids);
            claims.put("sids", sids);
            claims.put("roles", roles);
            claims.put("tenants", tenants);
            claims.put("tenantUids", tenantUids);
            return claims;
        }
    }

    public TenantClaims getTenantClaims(Resource resource, boolean includeRoleName) {
        var sids = new ArrayList<Integer>();
        sids.add(resource.getId());
        sids.addAll(listGroupSids(resource));
        var tenantRoles = roleRepository.listAssignedTenantRolesByIdentity(sids);
        var tenantSids = tenantRoles.stream()
                .filter(tenantRole -> tenantRole.getRoles().size() > 0)
                .map(tenantRole -> tenantRole.getTenant().getId())
                .collect(Collectors.toList());

        var roleIndex = new HashMap<String, RoleUserInfo>();
        var tenantIndex = new HashMap<String, TenantUserInfo>();
        for (var tenantRole : tenantRoles) {
            buildTenantRoleIndexes(tenantRole, roleIndex, tenantIndex, includeRoleName);
            // var tenantId = tenantRole.getTenant().getId();
            // var ancestorTenants = ResourceRegistry.getTenantAncestors(tenantId);
            // var hierarchy = ancestorTenants.stream()
            // .map(ancestor -> (TenantHierarchy) TenantHierarchy.builder()
            // .uid(ancestor.getUid())
            // .label(ancestor.getLabel())
            // .schemaLabel(ancestor.getSchemaLabel())
            // .build())
            // .toList();
            // tenantRole.getTenant().setHierarchy(hierarchy);
        }
        Set<String> tenantUids = null;
        if (storeTenantsInIdToken) {
            // build a Set of tenantIds from the tenantIndex
            tenantUids = tenantIndex.values().stream()
                    .filter(tenantUserInfo -> {
                        return tenantUserInfo.getSchemaLabel() != null
                                && storeTenantsSchemaLabels.contains(tenantUserInfo.getSchemaLabel());
                    })
                    .map(TenantUserInfo::getUid)
                    .collect(Collectors.toSet());
        }

        return new TenantClaims(
                tenantSids,
                sids,
                roleIndex,
                tenantIndex.get(Resource.toUid(Constants.TENANT_ROOT_ID)),
                tenantUids);

    }

    public Set<Integer> listGroupSids(@NonNull Resource resource) {
        Set<Integer> groupSids = groupMembershipRepository.listByRight(resource.toRef());
        if (groupSids.size() > 0) {
            var nestedGroupSids = groupRepository.listNestedGroupSidsUnsecure(groupSids);
            groupSids.addAll(nestedGroupSids);
        }
        return groupSids;
    }

    private void buildTenantRoleIndexes(TenantRole tenantRole, Map<String, RoleUserInfo> roleIndex,
            Map<String, TenantUserInfo> tenantIndex, boolean includeRoleName) {
        if (tenantRole != null) {
            for (var role : tenantRole.getRoles()) {
                var roleUserInfo = RoleUserInfo.builder()
                        .actions(role.getActions())
                        .schemas(role.getSchemas())
                        .build();
                if (includeRoleName == true) {
                    roleUserInfo.setLabel(role.getName());
                }
                roleIndex.put(role.getUid(), roleUserInfo);
            }
            var tenant = tenantRole.getTenant();

            var child = tenantIndex.get(tenant.getUid());
            if (child != null) {
                child.setRoles(tenantRole.getRoles().stream()
                        .map(role -> role.getUid())
                        .collect(Collectors.toSet()));
            } else {
                child = TenantUserInfo.builder()
                        .uid(tenant.getUid())
                        .label(tenant.getLabel())
                        .schemaLabel(tenant.getSchemaLabel())
                        .roles(tenantRole.getRoles().stream()
                                .map(role -> role.getUid())
                                .collect(Collectors.toSet()))
                        .build();
                tenantIndex.put(tenant.getUid(), child);

                var tenantId = tenant.getId();
                var ancestorTenants = ResourceRegistry.getTenantAncestors(tenantId);

                for (var ancestor : ancestorTenants) {
                    var parentTenantUserInfo = tenantIndex.get(ancestor.getUid());
                    if (parentTenantUserInfo != null) {
                        parentTenantUserInfo.getChildren().add(child);
                        break;
                    } else {
                        parentTenantUserInfo = TenantUserInfo.builder()
                                .uid(ancestor.getUid())
                                .label(ancestor.getLabel())
                                .schemaLabel(ancestor.getSchemaLabel())
                                .build();
                        parentTenantUserInfo.getChildren().add(child);
                        tenantIndex.put(ancestor.getUid(), parentTenantUserInfo);
                        child = parentTenantUserInfo;
                    }
                }
            }
        }
    }

    protected Group createGroup(String displayName, Integer tenantId, Integer groupRefTenantId) {
        return createGroup(displayName, tenantId, groupRefTenantId, false);
    }

    protected Group createGroup(String displayName, Integer tenantId, Integer groupRefTenantId, boolean unsecure) {
        var group = Group.builder()
                .label(displayName)
                .tenantId(tenantId)
                .build();
        return groupService.create(group, groupRefTenantId, unsecure);
    }

    protected void deleteGroup(Tenant tenant, String rgName, String groupName) {
        try {
            // TODO
            // groupService.deleteByLabel(rgName + "@" +
            // Resource.toSuffix(tenant.getLabel()), groupName);
        } catch (NotFoundException e) {
        }
    }

}
