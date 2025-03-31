package net.oneki.mtac.api;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.DefaultResourceService;
import net.oneki.mtac.resource.ResourceService;

public abstract class DefaultResourceController extends ResourceController<UpsertRequest, Resource, DefaultResourceService> {

    // @Autowired protected ResourceService<U,E> resourceService;

    // @Override
    // protected ResourceService<U,E> getService() {
    //     return resourceService;
    // }

 
}
