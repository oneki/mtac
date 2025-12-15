package net.oneki.mtac.test.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.util.security.MultiTenantResourceServerConfig;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig extends MultiTenantResourceServerConfig {
  // @formatter:off
    private static final List<RequestMatcher> PUBLIC_URLS = Arrays.asList(
      PathPatternRequestMatcher.withDefaults().matcher("/public/**"),
      PathPatternRequestMatcher.withDefaults().matcher("/oidc/**"),
      PathPatternRequestMatcher.withDefaults().matcher("/healthchecks/**"),
      PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
      PathPatternRequestMatcher.withDefaults().matcher("/api-docs*"),
      PathPatternRequestMatcher.withDefaults().matcher("/api-docs/**"),
      PathPatternRequestMatcher.withDefaults().matcher("/swagger*"),
      PathPatternRequestMatcher.withDefaults().matcher("/test"),
      PathPatternRequestMatcher.withDefaults().matcher("/api/oauth2/token"),
      PathPatternRequestMatcher.withDefaults().matcher("/api/.well-known/jwks.json")
    );

    @Override
    protected HttpSecurity configureFilterChain(HttpSecurity http) throws Exception {
      http.authorizeHttpRequests(c -> {
        c.requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
         .anyRequest().authenticated();
      });
      return http;
    }

    @Override
    protected List<RequestMatcher> getPublicUrls() {
        return PUBLIC_URLS;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }

    @Bean
    public OAuth2AuthorizedClientManager myAuthorizedClientManager() {
      return new OAuth2AuthorizedClientManager() {

        @Override
        public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest arg0) {
          // TODO Auto-generated method stub
          throw new UnsupportedOperationException("Unimplemented method 'authorize'");
        }
        
      };
    }

    @Override
    protected OpenIdService getOpenIdService() {
      // TODO Auto-generated method stub
      throw new UnsupportedOperationException("Unimplemented method 'getOpenIdService'");
    }

    /**
     * Indicate which entry in the JWT token list the roles of the user
     * By default, the stack assumes that the key is "roles"
     * Override the method getJwtAuthoritiesExtractor to indicate another key
     * or to implement more complex logic
     */
    // @Override
    // protected JwtAuthoritiesExtractor getJwtAuthoritiesExtractor() {
    //     return new DefaultJwtAuthoritiesExtractor(new String[]{"roles"});
    // }
}
