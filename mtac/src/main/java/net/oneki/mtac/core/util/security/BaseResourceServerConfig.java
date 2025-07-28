package net.oneki.mtac.core.util.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.core.util.security.DefaultJwtAuthoritiesExtractor;
import net.oneki.mtac.model.core.util.security.DefaultJwtAuthoritiesExtractor.AuthorityKey;
import net.oneki.mtac.model.core.util.security.JwtAuthoritiesExtractor;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.resource.iam.identity.user.UserService;

/**
 * BaseResourceServerConfig Expecting this config spring: security: oauth2:
 * resourceserver: jwt: issuer-uri: http://localhost:8080/oidc jwk-set-uri:
 * http://localhost:8080/oidc/.well-known/jwks.json
 */
@Slf4j
public abstract class BaseResourceServerConfig {
    protected static ObjectMapper objectMapper = new ObjectMapper();
    protected RequestMatcher protectedUrls = null;

    protected abstract List<RequestMatcher> getPublicUrls();

    protected abstract HttpSecurity configureFilterChain(HttpSecurity http) throws Exception;

    private List<RequestMatcher> allPublicUrls;

    @Autowired(required = false)
    protected JwtDecoder jwtDecoder;
    @Autowired(required = false)
    SecurityContext securityContext;

    @Autowired
    protected TokenRegistry tokenRegistry;
    @Autowired
    protected TokenRepository tokenRepository;

    @Bean
    public BearerTokenResolver bearerTokenResolver(MtacProperties mtacProperties) {
                var config = mtacProperties.getIam();
        return new BearerTokenResolver(config.getTokenLocation(), config.getAccessTokenCookieName());
    }

    @Bean
    @Order(SecurityProperties.BASIC_AUTH_ORDER)
    public SecurityFilterChain filterChain(HttpSecurity http, UserService<?,?> userService,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        http.exceptionHandling(c -> {
            c.accessDeniedHandler((request, response, accessDeniedException) -> {
                handleException(request, response, accessDeniedException);
            })
                    .authenticationEntryPoint((request, response, authException) -> {
                        handleException(request, response, authException);
                    });
        })
                .csrf(c -> c.disable())
                .formLogin(f -> f.disable())
                .httpBasic(h -> h.disable())
                .logout(l -> l.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        http = configureFilterChain(http);

        // http.sessionManagement()
        // .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        // .and()
        // .exceptionHandling()
        // //.defaultAuthenticationEntryPointFor(forbiddenEntryPoint(),
        // getProtectedUrls())
        // .accessDeniedHandler((request, response, accessDeniedException) -> {
        // handleException(request, response, accessDeniedException);
        // })
        // .authenticationEntryPoint((request, response, authException) -> {
        // handleException(request, response, authException);
        // })
        // .and()
        // .csrf().disable()
        // .formLogin().disable()
        // .httpBasic().disable()
        // .logout().disable()
        // .headers()
        // .frameOptions()
        // .sameOrigin();

        resourceServer(http, userService, bearerTokenResolver);
        return http.build();
    }

    protected void resourceServer(final HttpSecurity http, UserService userService,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        http.oauth2ResourceServer(o -> {
            o.bearerTokenResolver(bearerTokenResolver);
            o.jwt(j -> {
                j.authenticationManager(getAuthenticationManager(userService));
            });
        });
        // http.oauth2ResourceServer()
        // .jwt()
        // .authenticationManager(getAuthenticationManager());
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(new OrRequestMatcher(getPublicMatchers()));
    }

    @Bean
    AuthenticationEntryPoint forbiddenEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.FORBIDDEN);
    }

    protected void handleException(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) {
        try {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            JsonUtil.object2Outputstream(exception, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void handleException(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) {
        try {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            JsonUtil.object2Outputstream(exception, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // protected void handleException(HttpServletRequest request,
    // HttpServletResponse response,
    // Exception exception) {
    // String code = "";
    // String message = "";
    // HttpStatus httpStatus = null;
    // if (exception instanceof AccessDeniedException) {
    // code = "BSN_ACCESS_DENIED";
    // message = "Access denied";
    // httpStatus = HttpStatus.FORBIDDEN;
    // } else if (exception instanceof AuthenticationException) {
    // code = "BSN_AUTH_ERROR";
    // message = "Authentication error";
    // httpStatus = HttpStatus.UNAUTHORIZED;
    // } else {
    // code = "BSN_AUTH_UNEXPECTED";
    // message = "Authentication unexpected error";
    // httpStatus = HttpStatus.UNAUTHORIZED;
    // }
    // String id = RandomUtil.randomString(8);
    // JsonException.JsonExceptionBuilder<?, ?> builder =
    // JsonException.builder().id(id).code(code).message(message);
    // JsonException jsonException = builder.build();
    // String principal = "N/A";
    // List<String> roles = new ArrayList<>();
    // if (securityContext != null && securityContext.getSubject() != null) {
    // principal = securityContext.getSubject();
    // Collection<? extends GrantedAuthority> authorities =
    // securityContext.getAuthorities();
    // if (authorities != null) {
    // for (GrantedAuthority authority : authorities) {
    // roles.add(authority.getAuthority());
    // }
    // }
    // }
    // if (logw != null) {
    //         // @formatter:off
    //         logw.errorFull(
    //             log, 
    //             (Exception) exception, 
    //             Metadatas.of(
    //                 "errorCode", code, 
    //                 "errorId", id,
    //                 TopMeta.security, true,
    //                 TopMeta.type, "response",
    //                 TopMeta.httpMethod, request.getMethod(),
    //                 TopMeta.httpPath, request.getServletPath(),
    //                 TopMeta.status, "error",
    //                 "httpStatus", httpStatus.value(),
    //                 TopMeta.principal, principal,
    //                 "roles", roles
    //             ), 
    //             false, 
    //             exception.getMessage(), 
    //             false
    //         );
    //         // @formatter:on
    // }

    // response.setStatus(httpStatus.value());
    // response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    // response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

    // try {
    // JsonUtil.object2Outputstream(jsonException, response.getOutputStream());
    // } catch (Exception e) {
    // throw new RuntimeException(e);
    // }
    // }

    protected RequestMatcher getProtectedUrls() {
        if (protectedUrls == null) {
            protectedUrls = new NegatedRequestMatcher(new OrRequestMatcher(getPublicMatchers()));
        }
        return protectedUrls;
    }

    protected JwtAuthoritiesExtractor getJwtAuthoritiesExtractor() {
        return new DefaultJwtAuthoritiesExtractor(
                new AuthorityKey("roles", true),
                new AuthorityKey("scope"),
                new AuthorityKey("cognito:groups", true),
                new AuthorityKey("groups", true));
    }

    protected AuthenticationManager getAuthenticationManager(UserService userService) {
        return new JwtAuthenticationManager(getJwtAuthoritiesExtractor(), jwtDecoder, tokenRegistry, tokenRepository,
                userService);
    }

    protected List<RequestMatcher> getPublicMatchers() {
        if (allPublicUrls != null)
            return allPublicUrls;
        allPublicUrls = new ArrayList<>();
        List<RequestMatcher> matchers = getPublicUrls();
        if (matchers != null) {
            allPublicUrls.addAll(matchers);
        }

        allPublicUrls.add(new AntPathRequestMatcher("/health/readiness"));
        allPublicUrls.add(new AntPathRequestMatcher("/health/liveness"));
        allPublicUrls.add(new AntPathRequestMatcher("/v2/api-docs"));
        return allPublicUrls;
    }

}