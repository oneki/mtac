package net.oneki.mtac.test.api.resource.tenant.rg;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import net.oneki.mtac.api.ResourceController;

@RestController
public class ResourceGroupController extends ResourceController<ResourceGroupUpsertRequest, ResourceGroup, ResourceGroupService> {
    @Autowired protected ResourceGroupService resourceGroupService;

    @Override
    protected Class<ResourceGroupUpsertRequest> getRequestClass() {
        return ResourceGroupUpsertRequest.class;
    }

    @Override
    protected Class<ResourceGroup> getResourceClass() {
        return ResourceGroup.class;
    }

    @Override
    protected ResourceGroupService getService() {
        return resourceGroupService;
    }
}
