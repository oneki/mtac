package net.oneki.mtac.test.api.resource.tenant.site;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.oneki.mtac.model.core.dto.UpsertResponse;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.resource.iam.identity.group.DefaultGroupService;
import net.oneki.mtac.resource.tenant.TenantService;
import net.oneki.mtac.test.api.resource.tenant.rg.ResourceGroup;
import net.oneki.mtac.test.api.resource.tenant.rg.ResourceGroupService;

@Service
public class SiteService extends TenantService<SiteUpsertRequest, Site> {
    @Autowired
    protected ResourceGroupService rgService;
    @Autowired
    protected DefaultGroupService groupService;

    @Override
    public UpsertResponse<Site> create(SiteUpsertRequest request) {
        var siteResponse = super.create(request);
        var site = siteResponse.getResource();
        var assetsRg = createRg("assets", site.getId());
        var assetAdministrators = groupService.getByLabel(request.getTenant() + " Asset Administrators", site.getLabel());
        // Give permissions to Asset Administrators to manage all assets
        permissionService.addPermissionUnsecure("role_asset_admin", assetsRg.getId(), assetAdministrators.getId());

        return siteResponse;
    }

    @Override
    public UpsertResponse<Void> delete(Site site) {
        // deleteRg(site, "assets");
        return super.delete(site);
    }

    private ResourceGroup createRg(String name, Integer tenantId) {
        var rg = ResourceGroup.builder()
                .label(name)
                .tenantId(tenantId)
                .build();
        return rgService.create(rg).getResource();
    }

    // private void deleteRg(Site site, String rgName) {
    //     try {
    //         rgService.deleteByLabel(site.getLabel(), Tenant.toLabel(rgName, site.getLabel()));
    //     } catch (NotFoundException e) {
    //     }
    // }

    @Override
    public Class<Site> getEntityClass() {
        return Site.class;
    }

    @Override
    public Class<SiteUpsertRequest> getRequestClass() {
        return SiteUpsertRequest.class;
    }
}
