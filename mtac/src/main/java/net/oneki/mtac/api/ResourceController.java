package net.oneki.mtac.api;

import java.util.Map;

import org.atteo.evo.inflector.English;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.PostConstruct;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.model.core.util.StringUtils;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.ResourceService;

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
            RequestMappingInfo.paths(getApiPath() + "/{uid}")
                    .methods(RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            ResourceController.class.getDeclaredMethod("getByUid", String.class)
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
            RequestMappingInfo.paths(getApiPath() + "/{uid}")
                    .methods(RequestMethod.DELETE)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            ResourceController.class.getDeclaredMethod("deleteByUid", String.class)
        );  
        
        // list
        handlerMapping.registerMapping(
            RequestMappingInfo.paths(getApiPath())
                    .methods(RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            ResourceController.class.getDeclaredMethod("list", Map.class)
        );
    }

    public R getByUid(@PathVariable("uid") String uid) {
        return getService().getByUid(uid);
    }

    public R create(@RequestBody U request) {
        var result = getService().create(request);
        return result;
    }

    public void deleteByUid(@PathVariable("uid") String uid) {
        getService().deleteById(Resource.fromUid(uid));
    }

    // list. A query param is used to specify the tenant. If no tenant is specified, the tenant root is used.
    public Page<R> list(@RequestParam Map<String, String> parameters) {
        if (!parameters.containsKey("tenant")) {
            parameters.put("tenant", "root");
        }
        var query = Query.fromRest(parameters, getResourceClass());
        return getService().list(query);

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
