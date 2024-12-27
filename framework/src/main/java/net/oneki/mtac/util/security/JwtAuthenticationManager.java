package net.oneki.mtac.util.security;

import java.util.Collection;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import net.oneki.mtac.util.security.DefaultOAuth2User;
import net.oneki.mtac.util.security.JwtAuthoritiesExtractor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * JwtAuthenticationProvider
 */
@Data
@RequiredArgsConstructor
public class JwtAuthenticationManager implements AuthenticationManager {
    private final JwtAuthoritiesExtractor jwtAuthoritiesExtractor;
	private final JwtDecoder jwtDecoder;
	private String nameAttributeKey = "sub";
	private String authorizedClientRegistrationId = "default";

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof BearerTokenAuthenticationToken)) {
            throw new RuntimeException("JwtAuthenticationManager only support BearerTokenAuthenticationToken");
        }
        BearerTokenAuthenticationToken bearer = (BearerTokenAuthenticationToken) authentication;
		Jwt jwt;
		try {
			jwt = this.jwtDecoder.decode(bearer.getToken());
		} catch (JwtException failed) {
			OAuth2Error invalidToken = invalidToken(failed.getMessage());
			throw new OAuth2AuthenticationException(invalidToken, invalidToken.getDescription(), failed);
        }
        
        Collection<GrantedAuthority> authorities = jwtAuthoritiesExtractor.extratAuthorities(jwt);

        OAuth2User principal = new DefaultOAuth2User(authorities, jwt.getClaims(), nameAttributeKey, bearer.getToken());
        return new OAuth2AuthenticationToken(principal, authorities, "default");
    }


    private static final OAuth2Error DEFAULT_INVALID_TOKEN =
			invalidToken("An error occurred while attempting to decode the Jwt: Invalid token");
    private static OAuth2Error invalidToken(String message) {
		try {
			return new BearerTokenError(
					BearerTokenErrorCodes.INVALID_TOKEN,
					HttpStatus.UNAUTHORIZED,
					message,
					"https://tools.ietf.org/html/rfc6750#section-3.1");
		} catch (IllegalArgumentException malformed) {
			// some third-party library error messages are not suitable for RFC 6750's error message charset
			return DEFAULT_INVALID_TOKEN;
		}
	}
}