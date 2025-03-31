package net.oneki.mtac.core.util.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component("bearerTokenResolver")
public class BearerTokenResolver implements org.springframework.security.oauth2.server.resource.web.BearerTokenResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        String issuer = request.getHeader("X-Token-Issuer");

        if (StringUtils.isNotBlank(issuer)) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null) {
                String[] tokens = authorization.split(" ");
                if (tokens.length != 2) throw new RuntimeException("The authorization header is invalid");
                return tokens[1];
            } else {
                throw new RuntimeException("The authorization header is invalid");
            }
        } else {
            DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();
            return defaultBearerTokenResolver.resolve(request);
        }
    }

}
