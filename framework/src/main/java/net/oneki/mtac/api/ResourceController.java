package net.oneki.mtac.api;

import org.atteo.evo.inflector.English;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.PostConstruct;
import net.oneki.mtac.model.api.UpsertRequest;
import net.oneki.mtac.model.entity.Resource;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.service.ResourceService;
import net.oneki.mtac.util.StringUtils;

public abstract class ResourceController<T extends UpsertRequest, R extends Resource> {

    @Autowired protected ResourceRepository resourceRepository;
    @Autowired protected ResourceService resourceService;
    @Autowired protected RequestMappingHandlerMapping handlerMapping;
    @Value("${mtac.api.base-path:/api}") private String apiBasePath;

    protected abstract Class<T> getRequestClass();
    protected abstract Class<R> getResourceClass();

    @PostConstruct
    public void init() throws NoSuchMethodException {
        // getByLabelOrUrn
        handlerMapping.registerMapping(
            RequestMappingInfo.paths(getApiPath() + "/{label_or_urn}")
                    .methods(RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            ResourceController.class.getDeclaredMethod("getByLabelOrUrn", String.class)
        ); 
        
        // create
        handlerMapping.registerMapping(
            RequestMappingInfo.paths(getApiPath())
                    .methods(RequestMethod.POST)
                    .consumes(MediaType.APPLICATION_JSON_VALUE)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            ResourceController.class.getDeclaredMethod("create", UpsertRequest.class)
        );
    }

    public R getByLabelOrUrn(@PathVariable("label_or_urn") String labelOrUrn) {
        return resourceRepository.getByLabelOrUrn(labelOrUrn, getResourceClass());
    }

    public R create(@RequestBody T request) {
        var result = resourceService.create(request, getResourceClass());
        return result;
    }

    protected String getApiPath() {
        var result = apiBasePath;
        if (!result.endsWith("/")) {
            result += "/";
        }

        var tokens = this.getClass().getName().split("\\.");
        var name = tokens[tokens.length - 1];

        if (name.endsWith("Controller")) name = name.substring(0, name.length() - 10);

        var path = result + StringUtils.pascalToKebab(English.plural(name));
        System.out.println("path: " + path);
        return path;
    }


    
}
