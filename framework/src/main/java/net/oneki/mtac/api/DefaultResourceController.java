package net.oneki.mtac.api;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.resource.Resource;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.UpsertRequest;

public abstract class DefaultResourceController<U extends UpsertRequest, E extends Resource> extends ResourceController<U, E, ResourceService<U, E>> {

    @Autowired protected ResourceService<U,E> resourceService;

    @Override
    protected ResourceService<U,E> getService() {
        return resourceService;
    }

    


    
}
