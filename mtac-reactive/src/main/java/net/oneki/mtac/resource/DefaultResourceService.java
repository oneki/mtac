package net.oneki.mtac.resource;

import org.springframework.stereotype.Service;

import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;

@Service
public class DefaultResourceService extends ResourceService<UpsertRequest, Resource> {

    @Override
    public Class<Resource> getEntityClass() {
        return Resource.class;
    }

    @Override
    public Class<UpsertRequest> getRequestClass() {
        return UpsertRequest.class;
    }
    
}
