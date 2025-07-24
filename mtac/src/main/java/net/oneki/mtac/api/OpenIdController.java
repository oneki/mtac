package net.oneki.mtac.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.openid.JwksResponse;
import net.oneki.mtac.model.core.openid.LoginRequest;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@RequiredArgsConstructor
public abstract class OpenIdController {

    protected final JwtTokenService tokenService;
    protected final PermissionService permissionService;
    private final RequestMappingHandlerMapping handlerMapping;
    private final String apiBasePath;

    @PostConstruct
    public void init() throws NoSuchMethodException {

        /**
         * When "GET /simpleHandler" is called, invoke, parametrizedHandler(String,
         * HttpServletRequest) method.
         */
        handlerMapping.registerMapping(
                RequestMappingInfo.paths(apiBasePath + "/oauth2/token")
                        .methods(RequestMethod.POST)
                        .consumes(MediaType.APPLICATION_JSON_VALUE)
                        .produces(MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                this,
                OpenIdController.class.getDeclaredMethod("auth", LoginRequest.class)
        );

        handlerMapping.registerMapping(
            RequestMappingInfo.paths(apiBasePath + "/oauth2/token")
                    .methods(RequestMethod.POST)
                    .consumes(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            OpenIdController.class.getDeclaredMethod("auth", String.class, String.class, String.class)
        );

        handlerMapping.registerMapping(
            RequestMappingInfo.paths(apiBasePath + "/logout")
                    .methods(RequestMethod.POST)
                    .build(),
            this,
            OpenIdController.class.getDeclaredMethod("logout")
        );        

        handlerMapping.registerMapping(
            RequestMappingInfo.paths(apiBasePath + "/.well-known/jwks.json")
                    .methods(RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            OpenIdController.class.getDeclaredMethod("getJwks")
        ); 
        
        handlerMapping.registerMapping(
            RequestMappingInfo.paths(apiBasePath + "/oauth2/userinfo")
                    .methods(RequestMethod.GET)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build(),
            this,
            OpenIdController.class.getDeclaredMethod("userinfo")
        ); 

    }

    public ResponseEntity<?> auth(@RequestBody LoginRequest request) throws Exception {
        var result = getUserService().login(request.getClient_id(), request.getClient_secret());
        var response = ResponseEntity.ok(result);  
        return response;
    }

    public ResponseEntity<?> auth(@RequestParam(value = "client_id", required = true) String client_id,
            @RequestParam(value = "client_secret", required = true) String client_secret,
            @RequestParam(value = "grant_type", required = false) String grant_type) throws Exception {
        var result = getUserService().login(client_id, client_secret);
        var response = ResponseEntity.ok(result);  
        return response;
    }

    public Map<String, Object> userinfo() throws Exception {
        return getUserService().userinfo();
    }

    public ResponseEntity<?> logout() throws Exception {
        getUserService().logout();
        return ResponseEntity.ok().build();
    }

    public JwksResponse getJwks() {
        return JwksResponse.builder().key(tokenService.getJwkAsJsonObject()).build();
    }

    protected abstract UserService<?, ?> getUserService();
}
