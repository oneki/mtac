package net.oneki.mtac.reactive.test.api.resource.tenant.site;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.reactive.test.api.resource.tenant.rg.ResourceGroup;
import net.oneki.mtac.reactive.test.api.resource.tenant.rg.ResourceGroupService;
import net.oneki.mtac.resource.iam.identity.group.DefaultGroupService;
import net.oneki.mtac.resource.tenant.TenantService;
import reactor.core.publisher.Mono;

@Service
public class SiteService extends TenantService<SiteUpsertRequest, Site> {
    @Autowired
    protected ResourceGroupService rgService;
    @Autowired
    protected DefaultGroupService groupService;

    @Override
    public Mono<Site> create(SiteUpsertRequest request) {
        var site = super.create(request).block();
        var assetsRg = createRg("assets", site.getId()).block();
        var assetAdministrators = groupService.getByLabel(request.getTenant() + " Asset Administrators", site.getLabel()).block();
        // Give permissions to Asset Administrators to manage all assets
        permissionService.addPermissionUnsecure("role_asset_admin", assetsRg.getId(), assetAdministrators.getId()).block();

        return Mono.just(site);
    }

    @Override
    public Mono<Void> delete(Site site) {
        return deleteRg(site, "assets")
            .then(super.delete(site));
    }

    private Mono<ResourceGroup> createRg(String name, Integer tenantId) {
        var rg = ResourceGroup.builder()
                .name(name)
                .tenantId(tenantId)
                .build();
        return rgService.create(rg);
    }

    private Mono<Void> deleteRg(Site site, String rgName) {
        try {
            return rgService.deleteByLabel(site.getLabel(), Tenant.toLabel(rgName, site.getLabel()));
        } catch (NotFoundException e) {
            return Mono.empty();
        }
    }

    @Override
    public Class<Site> getEntityClass() {
        return Site.class;
    }

    @Override
    public Class<SiteUpsertRequest> getRequestClass() {
        return SiteUpsertRequest.class;
    }
}
