package net.oneki.mtac.api;

import java.util.Map;
import java.util.Set;

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
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.dto.UpsertResponse;
import net.oneki.mtac.model.core.resource.SearchDto;
import net.oneki.mtac.model.core.util.StringUtils;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.ResourceService;

@Slf4j
public abstract class ResourceController<U extends UpsertRequest, R extends Resource, S extends ResourceService<U, R>> {

    @Autowired
    protected ResourceRepository resourceRepository;
    @Autowired
    protected RequestMappingHandlerMapping handlerMapping;
    @Value("${mtac.api.base-path:/api}")
    protected String apiBasePath;

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
                ResourceController.class.getDeclaredMethod("getByUid", String.class, Set.class));

        // getParameters
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(getApiPath() + "/{uid}/parameters")
                        .methods(RequestMethod.GET)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                ResourceController.class.getDeclaredMethod("getParameters", String.class));

        // create
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(getApiPath())
                        .methods(RequestMethod.POST)
                        .consumes(MediaType.APPLICATION_JSON_VALUE)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                ResourceController.class.getDeclaredMethod("create", UpsertRequest.class));

        // updateParameters
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(getApiPath() + "/{uid}/parameters", getApiPath() + "/{uid}")
                        .methods(RequestMethod.PUT)
                        .consumes(MediaType.APPLICATION_JSON_VALUE)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                ResourceController.class.getDeclaredMethod("update", String.class, UpsertRequest.class));

        // delete
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(getApiPath() + "/{uid}")
                        .methods(RequestMethod.DELETE)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                ResourceController.class.getDeclaredMethod("deleteByUid", String.class));

        // list
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(getApiPath())
                        .methods(RequestMethod.GET)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                ResourceController.class.getDeclaredMethod("list", Map.class));

        // search
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(getApiPath() + "/search")
                        .methods(RequestMethod.GET)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                ResourceController.class.getDeclaredMethod("search", Map.class));
    }

    public R getByUid(@PathVariable("uid") String uid,
            @RequestParam(name = "relations", required = false) Set<String> relations) {
        if (relations == null || relations.isEmpty()) {
            return getService().getByUid(uid);
        } else {
            return getService().getByUid(uid, relations);
        }
    }

    public U getParameters(@PathVariable("uid") String uid) {
        var result = getService().getRequestByUid(uid);
        return result;
    }

    public UpsertResponse<R> create(@RequestBody U request) {
        var result = getService().create(request);
        return result;
    }

    public UpsertResponse<R> update(@PathVariable("uid") String uid, @RequestBody U request) {
        var result = getService().update(uid, request);
        return result;
    }

    public UpsertResponse<Void> deleteByUid(@PathVariable("uid") String uid) {
        return getService().deleteByUid(uid);
    }

    // list. A query param is used to specify the tenant. If no tenant is specified,
    // the tenant root is used.
    public Page<R> list(@RequestParam Map<String, String> parameters) {
        if (!parameters.containsKey("tenant")) {
            parameters.put("tenant", "" + Constants.TENANT_ROOT_ID);
        } else {
            parameters.put("tenant", "" + Resource.fromUid(parameters.get("tenant")));
        }
        var query = Query.fromRest(parameters, getResourceClass());
        return getService().list(query);

    }


    public Page<SearchDto> search(@RequestParam Map<String, String> parameters) {
        if (parameters.containsKey("tenant")) {
            parameters.put("tenant", "" + Resource.fromUid(parameters.get("tenant")));
        }
        if (!parameters.containsKey("sortBy")) {
            parameters.put("sortBy", "resource_type,asc;label,asc");
        } 
        var query = Query.fromRest(parameters, getResourceClass());
        return getService().search(query);

    }

    protected String getApiBasePath() {
        return apiBasePath;
    }

    protected String getApiPath() {
        var result = getApiBasePath();
        if (!result.endsWith("/")) {
            result += "/";
        }

        var tokens = this.getClass().getName().split("\\.");
        var name = tokens[tokens.length - 1];

        if (name.endsWith("Controller"))
            name = name.substring(0, name.length() - 10);

        var path = result + StringUtils.pascalToKebab(English.plural(name));
        log.info("Registering API path: {}", path);
        return path;
    }

}
