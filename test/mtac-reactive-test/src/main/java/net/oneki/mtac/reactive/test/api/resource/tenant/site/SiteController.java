package net.oneki.mtac.reactive.test.api.resource.tenant.site;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.api.ResourceController;

@RestController
@RequiredArgsConstructor
public class SiteController extends ResourceController<SiteUpsertRequest, Site, SiteService> {
    protected final SiteService siteService;

    @Override
    protected Class<SiteUpsertRequest> getRequestClass() {
        return SiteUpsertRequest.class;
    }

    @Override
    protected Class<Site> getResourceClass() {
        return Site.class;
    }

    @Override
    protected SiteService getService() {
        return siteService;
    }
}
