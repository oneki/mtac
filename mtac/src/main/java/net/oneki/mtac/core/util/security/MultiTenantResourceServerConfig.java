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
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import jakarta.servlet.http.HttpServletRequest;

import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.oidc.TokenRevocationService;
import net.oneki.mtac.core.util.security.JwtIssuerAuthenticationManagerResolver.JwtClaimIssuerConverter;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.config.IssuerConfig.IssuersProperties;
import net.oneki.mtac.framework.config.IssuerConfig.IssuersProperties.IssuerProperties;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
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
                appContext, tokenRegistry, tokenRepository, getOpenIdService(), mtacProperties);
        var issuerConverter = new JwtClaimIssuerConverter(bearerTokenResolver);

        // manager chosen by the issuer of the token and by the audience the request path requires
        AuthenticationManagerResolver<HttpServletRequest> resolver = request -> {
            var issuer = issuerConverter.convert(request);
            var audience = AudienceAuthenticationManagerResolver.audienceOf(request);
            var authenticationManager = trustedIssuerJwtAuthenticationManagerResolver.resolve(issuer, audience);
            if (authenticationManager == null) {
                throw new InvalidBearerTokenException("Invalid issuer");
            }
            return authenticationManager;
        };
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
        private final Map<String, JwtDecoder> jwtDecoders = new ConcurrentHashMap<>();
        private final Map<String, IssuerProperties> trustedIssuers;
        private final JwtAuthoritiesExtractor jwtAuthoritiesExtractor;
        private final ApplicationContext appContext;
        private final TokenRegistry tokenRegistry;
        private final TokenRepository tokenRepository;
        private final OpenIdService openIdService;
        private final MtacProperties mtacProperties; // null = no audience check (former behaviour)

        public TrustedIssuerJwtAuthenticationManagerResolver(IssuersProperties issuersProperties,
                JwtAuthoritiesExtractor jwtAuthoritiesExtractor, ApplicationContext appContext,
                TokenRegistry tokenRegistry, TokenRepository tokenRepository,
                OpenIdService openIdService) {
            this(issuersProperties, jwtAuthoritiesExtractor, appContext, tokenRegistry, tokenRepository, openIdService,
                    null);
        }

        public TrustedIssuerJwtAuthenticationManagerResolver(IssuersProperties issuersProperties,
                JwtAuthoritiesExtractor jwtAuthoritiesExtractor, ApplicationContext appContext,
                TokenRegistry tokenRegistry, TokenRepository tokenRepository,
                OpenIdService openIdService, MtacProperties mtacProperties) {
            this.mtacProperties = mtacProperties;
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

        /** Manager of an issuer for the console audience. */
        @Override
        public AuthenticationManager resolve(String issuer) {
            return resolve(issuer, TokenAudience.CONSOLE);
        }

        /**
         * @param audience key of the audience the request path requires (see
         *                 {@link TokenAudience})
         * @return null when the issuer is not trusted
         */
        public AuthenticationManager resolve(String issuer, String audience) {
            IssuerProperties trustedIssuer = this.trustedIssuers.get(issuer);
            if (this.trustedIssuers.containsKey(issuer) && trustedIssuer != null) {
                // one decoder per issuer (it caches the JWKS), one manager per issuer and audience
                var jwtDecoder = this.jwtDecoders.computeIfAbsent(issuer, k -> jwtDecoder(trustedIssuer));
                return this.authenticationManagers.computeIfAbsent(issuer + " " + audience, k -> {
                    var manager = new JwtAuthenticationManager(jwtAuthoritiesExtractor, jwtDecoder,
                            tokenRegistry, tokenRepository, openIdService, audience, mtacProperties);
                    manager.setTokenRevocations(tokenRevocations());
                    return manager;
                });
            }
            return null;
        }

        /** Revocation list of the access tokens, null when the application has none. */
        private TokenRevocationService tokenRevocations() {
            return appContext != null ? appContext.getBeanProvider(TokenRevocationService.class).getIfAvailable()
                    : null;
        }

        private JwtDecoder jwtDecoder(IssuerProperties trustedIssuer) {
            if (StringUtils.isNotBlank(trustedIssuer.getDecoderUri())) {
                // Get the bean extending JwtDecoder to use; it must accept the typ at+jwt itself
                // (see JwtAuthenticationManager.accessTokenValidator)
                return (JwtDecoder) appContext.getBean(trustedIssuer.getJwtDecoderBean());
            }
            var jwtDecoder = NimbusJwtDecoder.withJwkSetUri(trustedIssuer.getJwkSetUri()).build();
            jwtDecoder.setJwtValidator(JwtAuthenticationManager.accessTokenValidator());
            return jwtDecoder;
        }
    }

}