package net.oneki.mtac.core.service.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.acl.AceRepository;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.security.Acl;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.PropertyPath;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.Role;
import net.oneki.mtac.resource.iam.RoleRepository;

@Service
@RequiredArgsConstructor

// User:
// - acme in the group installer (id: 8 et 24)
// - employee1 in the group deplasseEmployees (id: 9 et 25)
// RG installer1@deplasse.brussel (id: 30)
// Role
// - installer (id: 100)
// - viewer (id: 101)

// ACE:
// identityId RoleId ResourceId
// 8 100 30
// 25 101 30

// Role (cached)
// {
// "actions": [
// "asset.*|*",
// "iam.identity.user|reset_password"
// ]
// }

// Resource Asset
// {
// // created dynamically via getResource
// acl: [{
// identityId: 8,
// roleId: 100
// }, {
// identityId: 25,
// roleId: 101
// }
// }

// List / Get resource
// -> SQL does the filtering based on permission(in table resource_ace)

// Other action on resource
// -> first, do a getResource in DB (see above)
// -> for each ACE (created dynamically via getResource)
// - is loggedUser has the identityId of the ACE
// - if no -> continue the loop
// - if yes
// -> get the role in cache
// -> loop over actions
// - if matching, give access and return

// Special case for create
// - If we want to create an Asset, instead of doing a getResource on a specific
// asset
// we do a getResource on the resourceGroup in which we want to create the asset
public class PermissionService {
    protected ResourceRepository resourceRepository;
    protected RoleRepository roleRepository;
    protected AceRepository aceRepository;

    public void addPermission(String roleName, Integer resourceId, Integer identityId) {
        var role = resourceRepository.getByUniqueLabel(roleName, Role.class);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "The role with label " + roleName + " doesn't exist.");
        }
        addPermission(role.getId(), resourceId, identityId);
    }

    public void addPermissionUnsecure(String roleName, Integer resourceId, Integer identityId) {
        var role = resourceRepository.getByUniqueLabelUnsecure(roleName, Role.class);
        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "The role with label " + roleName + " doesn't exist.");
        }
        addPermission(role.getId(), resourceId, identityId);
    }

    public void addPermission(Integer roleId, Integer resourceId, Integer identityId) {
        aceRepository.create(roleId, resourceId, identityId);
    }

    public boolean hasPermission(Ref resourceRef, String permission) {
        var resource = resourceRepository.getById(resourceRef.getId(), Resource.class);
        return hasPermission(resource, permission);
    }

    public boolean hasPermission(String label, String tenantLabel, String schemaLabel, String permission) {
        return hasPermission(label, tenantLabel, Resource.class, permission);
    }

    public boolean hasPermission(String label, String tenantLabel, Class<? extends Resource> resourceClass,
            String permission) {
        var resource = resourceRepository.getByLabel(label, tenantLabel, resourceClass);
        return hasPermission(resource, permission);
    }

    /**
     * Check if the current user has the given permission on the given resource
     * Example of permission: "create", "reboot_cp"
     */
    public boolean hasPermission(int resourceId, String permission) {
        // getById checks if the resource exists and if the user has access to it based
        // on the type of the resource (= coarse grain)
        // Example: Does the user has access to the resource "company1" of type
        // "tenant.company"?
        var resource = resourceRepository.getById(resourceId, Resource.class);
        return hasPermission(resource, permission);
    }

    public boolean hasPermission(Resource resource, String permission) {
        if (resource == null) {
            return false;
        }

        // Now that we have access to the resource, we need to check what permission we
        // have on this resource
        // Example: Does the user has the permission "update" on the resource "company1"
        // of type "tenant.company"?
        return hasPermissionByPath(resource.getAcl(), resource.getSchemaLabel(), permission);
    }

    public boolean hasCreatePermission(String tenantLabel, Class<? extends Resource> resourceToCreateClass) {
        if (tenantLabel == null) {
            tenantLabel = Constants.TENANT_ROOT_LABEL;
        }
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        return hasCreatePermission(tenantId, resourceToCreateClass);
    }

    public boolean hasCreatePermission(String tenantLabel, String schemaLabel) {
        if (tenantLabel == null) {
            tenantLabel = Constants.TENANT_ROOT_LABEL;
        }
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        return hasCreatePermission(tenantId, schemaLabel);
    }

    /**
     * The permission create is a bit special as we need the permission create on
     * the tenant to be able to create a resource
     * We first need to retrieve the role we have on this tenant. The resource
     * "role" has the ACL described in the field content
     * Example of role: role.content = {"fields": ["*"], "actions":
     * ["tenant.company|create"], "schemas": ["*"]}
     * 
     * @param tenant
     * @param schemaLabel
     * @return
     */
    public boolean hasCreatePermission(Integer tenantId, Class<? extends Resource> resourceToCreateClass) {
        if (tenantId == null) {
            tenantId = Constants.TENANT_ROOT_ID;
        }
        var schemaLabel = ResourceRegistry.getSchemaByClass(resourceToCreateClass);
        return hasCreatePermission(tenantId, schemaLabel);
    }

    public boolean hasCreatePermission(Integer tenantId, String schemaLabel) {
        if (schemaLabel == null) {
            throw new BusinessException("SCHEMA_NOT_FOUND",
                    "The schema with label " + schemaLabel + " doesnt't exist.");
        }
        if (tenantId == null) {
            tenantId = Constants.TENANT_ROOT_ID;
        }
        // Retrieve all roles that are directly assigned to the given tenant for a user
        var roles = roleRepository.listCompiledRolesByTenant(tenantId);
        var permission = new PropertyPath(schemaLabel, "create"); // Example: "tenant.company|create"

        for (var role : roles) {
            var propertyPaths = PropertyPath.of(role.getActions());
            if (propertyPaths != null) {
                for (var propertyPath : propertyPaths) {
                    if (propertyPath.contains(permission)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    protected boolean hasPermissionByPath(Acl acl, String schemaLabel, String permission) {
        // var permissionSet = ResourceRegistry.getPermissionSet(schemaLabel);
        // if (permissionSet == null) {
        // throw new UnexpectedException("PERMISSION_SET_NOT_FOUND",
        // "The permission set for schemaLabel " + schemaLabel + " doesn't exist.");
        // }
        // if (!permissionSet.hasPermission(permission)) {
        // throw new BusinessException("PERMISSION_NOT_FOUND",
        // "The permission " + permission + " doesn't exist for schemaLabel " +
        // schemaLabel);
        // }

        var permissionPath = new PropertyPath(schemaLabel, permission); // Example: "iam.identity.user|reset_password"

        for (var ace : acl) {
            var propertyPaths = PropertyPath.of(ace.getActions());
            if (propertyPaths != null) {
                for (var propertyPath : propertyPaths) {
                    if (propertyPath.contains(permissionPath)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Autowired
    public void setResourceRepository(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Autowired
    public void setRoleRepository(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Autowired
    public void setAceRepository(AceRepository aceRepository) {
        this.aceRepository = aceRepository;
    }

}
