package net.oneki.mtac.core.util.security;

import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import jakarta.servlet.http.HttpServletRequest;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacIam.TokenLocation;

// @Component("bearerTokenResolver")
public class BearerTokenResolver
        implements org.springframework.security.oauth2.server.resource.web.BearerTokenResolver {
    MtacProperties mtacProperties;

    public BearerTokenResolver() {
        System.out.println("defaeult BearerTokenResolver");
    }

    public BearerTokenResolver(MtacProperties mtacProperties) {
        this.mtacProperties = mtacProperties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        var locations = mtacProperties.getIam().getTokenLocation();
        for (var location : locations) {
            if (location == TokenLocation.request_param) {
                var paramName = mtacProperties.getIam().getRequestParamTokenName();
                var token = request.getParameter(paramName);
                if (token != null) {
                    return token;
                }
            } else if (location == TokenLocation.cookie) {
                var cookieName = mtacProperties.getIam().getAccessTokenCookieName();
                if (request.getCookies() != null) {
                    for (var cookie : request.getCookies()) {
                        if (cookieName.equals(cookie.getName())) {
                            return cookie.getValue();
                        }
                    }
                }
            } else {
                DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
                var result = defaultBearerTokenResolver.resolve(request);
                if (result != null) {
                    return result;
                }
            }
        }

        DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
        var result = defaultBearerTokenResolver.resolve(request);
        return result;
        
    }

}
