package net.oneki.mtac.test.api.resource.tenant.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.resource.iam.identity.group.DefaultGroupService;
import net.oneki.mtac.resource.tenant.TenantService;
import net.oneki.mtac.test.api.resource.tenant.rg.ResourceGroup;
import net.oneki.mtac.test.api.resource.tenant.rg.ResourceGroupService;

public abstract class CompanyService<U extends CompanyUpsertRequest, E extends Company> extends TenantService<U, E> {
    @Autowired protected ResourceGroupService rgService;
    @Autowired protected DefaultGroupService groupService;
    @Autowired protected PermissionService permissionService;

    @Override
    public E create(U request) {
        var company = super.create(request);

        // Create resource groups
        var usersRg = createRg("users", company.getId());
        var groupsRg = createRg("groups", company.getId());
        var adminGroupsRg = createRg("admin-groups", company.getId());  
        var assetsRg = createRg("assets", company.getId());

        // share groups of the installer
        Group installerAdminGroup = null;
        Group installerAssetAdminGroup = null;
        if (request.getAdminGroup() != null) {
            installerAdminGroup = groupService.getByLabelOrUrn(request.getAdminGroup());
            groupService.createLink(installerAdminGroup, company.getId(), LinkType.Ref, true);
        }

        if (request.getAssetAdminGroup() != null) {
            installerAssetAdminGroup = groupService.getByLabelOrUrn(request.getAssetAdminGroup());
            groupService.createLink(installerAssetAdminGroup, company.getId(), LinkType.Ref, true);
        }
        
        // Create groups
        var iamAdministrators = createGroup(company.getName() + " IAM Administrators", groupsRg.getId(), company.getId(), List.of());
        var administrators = createGroup(company.getName() + " Administrators", adminGroupsRg.getId(), company.getId(), installerAdminGroup != null ? List.of(installerAdminGroup) : List.of());
        var assetAdministrators = createGroup(company.getName() + " Asset Administrators", adminGroupsRg.getId(), company.getId(), installerAssetAdminGroup != null ? List.of(installerAssetAdminGroup) : List.of());

        // Give permissions to IAM Administrators to manage groupsRg and usersRg
        permissionService.addPermissionUnsecure("role_user_admin", usersRg.getId(), iamAdministrators.getId());
        permissionService.addPermissionUnsecure("role_group_admin", groupsRg.getId(), iamAdministrators.getId());

        // Give permissions to Administrators (= installers in general) to manage the company
        permissionService.addPermissionUnsecure("role_admin", company.getId(), administrators.getId());

        // Give permissions to Asset Administrators to manage all assets
        permissionService.addPermissionUnsecure("role_asset_admin", assetsRg.getId(), assetAdministrators.getId());


        

        return company;
    }

    @Override
    public void delete(E company) {
        deleteGroup(company, "groups", "IAM Administrators");
        deleteGroup(company, "admin-groups", "Administrators");
        deleteGroup(company, "admin-groups", "Asset Administrators");
        deleteRg(company, "admin-groups");
        deleteRg(company, "groups");
        deleteRg(company, "users");
        deleteRg(company, "assets");
        super.delete(company);
    }

    private ResourceGroup createRg(String name, Integer tenantId) {
        var rg = ResourceGroup.builder()
            .name(name)
            .tenantId(tenantId)
            .build();
        return rgService.create(rg);
    }

    private Group createGroup(String name, Integer tenantId, Integer companyId, List<Identity> members) {
        var group = Group.builder()
            .name(name)
            .tenantId(tenantId)
            .members(members)
            .build();
        return groupService.create(group, companyId);
    }

    private void deleteGroup(E company, String rgName, String groupName) {
        try {
            groupService.deleteByLabel(rgName + "@" + company.getLabel(), company.getLabel() + " " + groupName);
        } catch (NotFoundException e) {}
    }

    private void deleteRg(E company, String rgName) {
        try {
            rgService.deleteByLabel(company.getLabel(),  rgName + "@" + company.getLabel());
        } catch (NotFoundException e) {}
    }

    // private Group createGroupRef(String name, Integer tenantId) {
    //     var group = GroupRef.builder()
    //         .name(name)
    //         .build();
    //     group = R.inject(group, tenantId);
    //     return group;
    // }
}
