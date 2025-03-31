package net.oneki.mtac.test.api.resource.tenant.rg;

import org.springframework.stereotype.Service;

import net.oneki.mtac.resource.tenant.TenantService;

@Service
public class ResourceGroupService extends TenantService<ResourceGroupUpsertRequest, ResourceGroup> {

    @Override
    public Class<ResourceGroup> getEntityClass() {
        return ResourceGroup.class;
    }

    @Override
    public Class<ResourceGroupUpsertRequest> getRequestClass() {
        return ResourceGroupUpsertRequest.class;
    }
    
}
