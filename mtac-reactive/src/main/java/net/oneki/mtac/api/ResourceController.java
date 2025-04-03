package net.oneki.mtac.api;

import org.atteo.evo.inflector.English;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.PostConstruct;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.model.core.util.StringUtils;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.ResourceService;
import reactor.core.publisher.Mono;

public abstract class ResourceController<U extends UpsertRequest, R extends Resource, S extends ResourceService<U, R>> {

    @Autowired protected ResourceRepository resourceRepository;
    @Autowired protected RequestMappingHandlerMapping handlerMapping;
    @Value("${mtac.api.base-path:/api}") private String apiBasePath;

    protected abstract Class<U> getRequestClass();
    protected abstract Class<R> getResourceClass();
    protected abstract S getService();

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

        // delete
        handlerMapping.registerMapping(
            RequestMappingInfo.paths(getApiPath() + "/{urn}")
                    .methods(RequestMethod.DELETE)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            ResourceController.class.getDeclaredMethod("deleteByUrn", String.class)
        );         
    }

    public Mono<R> getByLabelOrUrn(@PathVariable("label_or_urn") String labelOrUrn) {
        return resourceRepository.getByLabelOrUrn(labelOrUrn, getResourceClass());
    }

    public R create(@RequestBody U request) {
        var result = getService().create(request);
        return result;
    }

    public void deleteByUrn(@PathVariable("urn") String urn) {
        getService().deleteByUrn(urn);
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
        return path;
    }


    
}
