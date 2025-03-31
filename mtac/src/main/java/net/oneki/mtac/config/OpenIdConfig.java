package net.oneki.mtac.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import net.oneki.mtac.api.OpenIdController;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@Configuration
@ConditionalOnProperty(prefix = "mtac.openid.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenIdConfig {

    @RestController
    public class DefaultOpenIdAPI extends OpenIdController {

        public DefaultOpenIdAPI(UserService userService, JwtTokenService tokenService,
                PermissionService permissionService, RequestMappingHandlerMapping handlerMapping, @Value("${mtac.api.base-path:/api}") String apiBasePath) {
            super(userService, tokenService, permissionService, handlerMapping, apiBasePath);
        }
    }
}
