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

import net.oneki.mtac.config.IssuerConfig.IssuersProperties;
import net.oneki.mtac.config.IssuerConfig.IssuersProperties.IssuerProperties;
import net.oneki.mtac.core.util.security.JwtAuthoritiesExtractor;


public abstract class MultiTenantResourceServerConfig extends BaseResourceServerConfig {
    @Autowired IssuersProperties issuersProperties;

    @Autowired private ApplicationContext appContext;
    
    protected void resourceServer(final HttpSecurity http) throws Exception {
        JwtIssuerAuthenticationManagerResolver resolver = new JwtIssuerAuthenticationManagerResolver(new TrustedIssuerJwtAuthenticationManagerResolver(issuersProperties, getJwtAuthoritiesExtractor(), appContext));
        http.oauth2ResourceServer().authenticationManagerResolver(resolver);
    }

    public static class TrustedIssuerJwtAuthenticationManagerResolver
        implements AuthenticationManagerResolver<String> {

        private final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();
        private final Map<String, IssuerProperties> trustedIssuers;
        private final JwtAuthoritiesExtractor jwtAuthoritiesExtractor;
        private final ApplicationContext appContext;

        public TrustedIssuerJwtAuthenticationManagerResolver(IssuersProperties issuersProperties, JwtAuthoritiesExtractor jwtAuthoritiesExtractor, ApplicationContext appContext) {
            this.trustedIssuers = new HashMap<>();
            if (issuersProperties != null) {
                Map<String, IssuerProperties> trustedIssuers = issuersProperties.getTrustedIssuers();
                if (trustedIssuers != null) {
                    for(IssuerProperties issuer : trustedIssuers.values()) {
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
                return this.authenticationManagers.computeIfAbsent(issuer, k -> new JwtAuthenticationManager(jwtAuthoritiesExtractor, jwtDecoder));
            }
            return null;
        }
    } 
  
}