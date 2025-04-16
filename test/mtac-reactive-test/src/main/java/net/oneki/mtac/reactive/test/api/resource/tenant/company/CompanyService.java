package net.oneki.mtac.reactive.test.api.resource.tenant.company;

import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.reactive.test.api.resource.tenant.rg.ResourceGroup;
import net.oneki.mtac.reactive.test.api.resource.tenant.rg.ResourceGroupService;
import net.oneki.mtac.resource.iam.identity.group.DefaultGroupService;
import net.oneki.mtac.resource.tenant.TenantService;
import reactor.core.publisher.Mono;

public abstract class CompanyService<U extends CompanyUpsertRequest, E extends Company> extends TenantService<U, E> {
    @Autowired protected ResourceGroupService rgService;
    @Autowired protected DefaultGroupService groupService;
    @Autowired protected PermissionService permissionService;

    @Override
    public Mono<E> create(U request) {
        var company = super.create(request).block();

        // Create resource groups
        var usersRg = createRg("users", company.getId()).block();
        var groupsRg = createRg("groups", company.getId()).block();
        var adminGroupsRg = createRg("admin-groups", company.getId()).block();  
        var assetsRg = createRg("assets", company.getId()).block();

        // share groups of the installer
        Group installerAdminGroup = null;
        Group installerAssetAdminGroup = null;
        if (request.getAdminGroup() != null) {
            installerAdminGroup = groupService.getByLabelOrUrn(request.getAdminGroup())
                .flatMap(iag -> {
                    return groupService.createLink(iag, company.getId(), LinkType.Ref, true)
                        .then(Mono.just(iag));
                }).block();
            ;
            
        }

        if (request.getAssetAdminGroup() != null) {
            installerAssetAdminGroup = groupService.getByLabelOrUrn(request.getAssetAdminGroup())
                .flatMap(iag -> {
                    return groupService.createLink(iag, company.getId(), LinkType.Ref, true)
                        .then(Mono.just(iag));
                }).block();
        }
        
        // Create groups
        var iamAdministrators = createGroup(company.getName() + " IAM Administrators", groupsRg.getId(), company.getId(), List.of()).block();
        var administrators = createGroup(company.getName() + " Administrators", adminGroupsRg.getId(), company.getId(), installerAdminGroup != null ? List.of(installerAdminGroup) : List.of()).block();
        var assetAdministrators = createGroup(company.getName() + " Asset Administrators", adminGroupsRg.getId(), company.getId(), installerAssetAdminGroup != null ? List.of(installerAssetAdminGroup) : List.of()).block();

        // Give permissions to IAM Administrators to manage groupsRg and usersRg
        permissionService.addPermissionUnsecure("role_user_admin", usersRg.getId(), iamAdministrators.getId())
            .then(permissionService.addPermissionUnsecure("role_group_admin", groupsRg.getId(), iamAdministrators.getId()))
            .then(permissionService.addPermissionUnsecure("role_admin", company.getId(), administrators.getId()))
            .then(permissionService.addPermissionUnsecure("role_asset_admin", assetsRg.getId(), assetAdministrators.getId()));


        return Mono.just(company);
    }

    @Override
    public Mono<Void> delete(E company) {
        return deleteGroup(company, "groups", "IAM Administrators")
            .then(deleteGroup(company, "admin-groups", "IAM Administrators"))
            .then(deleteGroup(company, "admin-groups", "Administrators"))
            .then(deleteGroup(company, "admin-groups", "Asset Administrators"))
            .then(deleteRg(company, "admin-groups"))
            .then(deleteRg(company, "groups"))
            .then(deleteRg(company, "users"))
            .then(deleteRg(company, "assets"))
            .then(super.delete(company));
    }

    private Mono<ResourceGroup> createRg(String name, Integer tenantId) {
        var rg = ResourceGroup.builder()
            .name(name)
            .tenantId(tenantId)
            .build();
        return rgService.create(rg);
    }

    private Mono<Group> createGroup(String name, Integer tenantId, Integer companyId, List<Identity> members) {
        var group = Group.builder()
            .name(name)
            .tenantId(tenantId)
            .members(members)
            .build();
        return groupService.create(group, companyId);
    }

    private Mono<Void> deleteGroup(E company, String rgName, String groupName) {
        try {
            return groupService.deleteByLabel(rgName + "@" + company.getLabel(), company.getLabel() + " " + groupName);
        } catch (NotFoundException e) {
            return Mono.empty();
        }
    }

    private Mono<Void> deleteRg(E company, String rgName) {
        try {
            return rgService.deleteByLabel(company.getLabel(),  rgName + "@" + company.getLabel());
        } catch (NotFoundException e) {
            return Mono.empty();
        }
    }

    // private Group createGroupRef(String name, Integer tenantId) {
    //     var group = GroupRef.builder()
    //         .name(name)
    //         .build();
    //     group = R.inject(group, tenantId);
    //     return group;
    // }
}
