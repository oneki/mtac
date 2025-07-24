package net.oneki.mtac.core.util.security;

import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import jakarta.servlet.http.HttpServletRequest;

// @Component("bearerTokenResolver")
public class BearerTokenResolver implements org.springframework.security.oauth2.server.resource.web.BearerTokenResolver {
    String tokenLocation;
    String cookieName;

    public BearerTokenResolver() {
        System.out.println("defaeult BearerTokenResolver");
    }
    public BearerTokenResolver(String tokenLocation, String cookieName) {
        this.tokenLocation = tokenLocation;
        this.cookieName = cookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
         if ("cookie".equalsIgnoreCase(tokenLocation)) {
            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    if (cookieName.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }

        DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
        var result = defaultBearerTokenResolver.resolve(request);
        return result;
        
    }

}
