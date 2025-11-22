package net.oneki.mtac.core.util.security;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.util.security.JwtIssuerAuthenticationManagerResolver.JwtClaimIssuerConverter;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.config.IssuerConfig.IssuersProperties;
import net.oneki.mtac.framework.config.IssuerConfig.IssuersProperties.IssuerProperties;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.model.core.util.security.JwtAuthoritiesExtractor;

public abstract class MultiTenantResourceServerConfig extends BaseResourceServerConfig {
    @Autowired
    IssuersProperties issuersProperties;

    @Autowired
    private ApplicationContext appContext;

    @Override
    protected void resourceServer(final HttpSecurity http,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        var trustedIssuerJwtAuthenticationManagerResolver = new TrustedIssuerJwtAuthenticationManagerResolver(
                issuersProperties, getJwtAuthoritiesExtractor(),
                appContext, tokenRegistry, tokenRepository, getOpenIdService());
        var issuerConverter = new JwtClaimIssuerConverter(bearerTokenResolver);

        JwtIssuerAuthenticationManagerResolver resolver = new JwtIssuerAuthenticationManagerResolver(
                trustedIssuerJwtAuthenticationManagerResolver, issuerConverter);
        http.oauth2ResourceServer(o -> {
            o.bearerTokenResolver(bearerTokenResolver);
            o.authenticationManagerResolver(resolver);
            // o.jwt(j -> {
            // j.authenticationManager(getAuthenticationManager(userService));
            // });
        });
        // http.oauth2ResourceServer().authenticationManagerResolver(resolver);
    }

    public static class TrustedIssuerJwtAuthenticationManagerResolver
            implements AuthenticationManagerResolver<String> {

        private final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();
        private final Map<String, IssuerProperties> trustedIssuers;
        private final JwtAuthoritiesExtractor jwtAuthoritiesExtractor;
        private final ApplicationContext appContext;
        private final TokenRegistry tokenRegistry;
        private final TokenRepository tokenRepository;
        private final OpenIdService openIdService;

        public TrustedIssuerJwtAuthenticationManagerResolver(IssuersProperties issuersProperties,
                JwtAuthoritiesExtractor jwtAuthoritiesExtractor, ApplicationContext appContext,
                TokenRegistry tokenRegistry, TokenRepository tokenRepository,
                OpenIdService openIdService) {
            this.tokenRegistry = tokenRegistry;
            this.tokenRepository = tokenRepository;
            this.openIdService = openIdService;
            this.trustedIssuers = new HashMap<>();
            if (issuersProperties != null) {
                Map<String, IssuerProperties> trustedIssuers = issuersProperties.getTrustedIssuers();
                if (trustedIssuers != null) {
                    for (IssuerProperties issuer : trustedIssuers.values()) {
                        this.trustedIssuers.put(issuer.getIssuerUri(), issuer);
                    }
                }
            }
            this.jwtAuthoritiesExtractor = jwtAuthoritiesExtractor;
            this.appContext = appContext;
        }

        @Override
        public AuthenticationManager resolve(String issuer) {
            IssuerProperties trustedIssuer = this.trustedIssuers.get(issuer);
            if (this.trustedIssuers.containsKey(issuer) && trustedIssuer != null) {
                JwtDecoder jwtDecoder;
                if (trustedIssuer != null && StringUtils.isNotBlank(trustedIssuer.getDecoderUri())) {
                    // Get the bean extending JwtDecoder to use
                    jwtDecoder = (JwtDecoder) appContext.getBean(trustedIssuer.getJwtDecoderBean());
                } else {
                    jwtDecoder = NimbusJwtDecoder.withJwkSetUri(trustedIssuer.getJwkSetUri()).build();
                }
                return this.authenticationManagers.computeIfAbsent(issuer,
                        k -> new JwtAuthenticationManager(jwtAuthoritiesExtractor, jwtDecoder, tokenRegistry,
                                tokenRepository, openIdService));
            }
            return null;
        }
    }

}