package net.oneki.mtac.resource;

import org.springframework.stereotype.Service;

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
