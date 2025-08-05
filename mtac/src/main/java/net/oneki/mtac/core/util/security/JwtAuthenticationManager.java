package net.oneki.mtac.core.util.security;

import java.util.ArrayList;
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

import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.model.core.util.security.DefaultOAuth2User;
import net.oneki.mtac.model.core.util.security.JwtAuthoritiesExtractor;
import net.oneki.mtac.resource.iam.identity.user.UserService;

/**
 * JwtAuthenticationProvider
 */
@Data
@RequiredArgsConstructor
public class JwtAuthenticationManager implements AuthenticationManager {
	private final JwtAuthoritiesExtractor jwtAuthoritiesExtractor;
	private final JwtDecoder jwtDecoder;
	private final TokenRegistry tokenRegistry;
	private final TokenRepository tokenRepository;
	private final OpenIdService openIdService;
	private String nameAttributeKey = "username";
	private String authorizedClientRegistrationId = "default";

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		if (!(authentication instanceof BearerTokenAuthenticationToken)) {
			throw new RuntimeException("JwtAuthenticationManager only support BearerTokenAuthenticationToken");
		}
		BearerTokenAuthenticationToken bearer = (BearerTokenAuthenticationToken) authentication;
		Jwt jwt;
		var accessToken = bearer.getToken();
		var tokenRefreshed = false;
		try {
/* 			// check first if the token is not expired
			// if it's the case, try to refresh it
			try {
				var parsedJwt = SignedJWT.parse(accessToken);
				var exp = parsedJwt.getJWTClaimsSet().getExpirationTime();
				var sub = parsedJwt.getJWTClaimsSet().getSubject();
				if (exp != null && exp.before(new Date())) {
					// token is expired, try to refresh it
					accessToken = userService.refreshToken(sub, accessToken);
					tokenRefreshed = true;
				}
			} catch (Exception e) {
				// if the refresh fails, do nothing, the token will be considered invalid
			} */
			
			jwt = this.jwtDecoder.decode(accessToken);
			var sub = jwt.getClaimAsString("sub");
			if (sub == null) {
				throw new JwtException("Missing claim 'sub' in JWT token");
			}
			var claims = openIdService.userinfo(sub, false);
			Collection<GrantedAuthority> authorities = new ArrayList<>(); // TODO

			OAuth2User principal = new DefaultOAuth2User(authorities, claims, nameAttributeKey, accessToken, tokenRefreshed);
			return new OAuth2AuthenticationToken(principal, authorities, "default");
		} catch (JwtException failed) {
			OAuth2Error invalidToken = invalidToken(failed.getMessage());
			throw new OAuth2AuthenticationException(invalidToken, invalidToken.getDescription(), failed);
		}

	}

	private static final OAuth2Error DEFAULT_INVALID_TOKEN = invalidToken(
			"An error occurred while attempting to decode the Jwt: Invalid token");

	private static OAuth2Error invalidToken(String message) {
		try {
			return new BearerTokenError(
					BearerTokenErrorCodes.INVALID_TOKEN,
					HttpStatus.UNAUTHORIZED,
					message,
					"https://tools.ietf.org/html/rfc6750#section-3.1");
		} catch (IllegalArgumentException malformed) {
			// some third-party library error messages are not suitable for RFC 6750's error
			// message charset
			return DEFAULT_INVALID_TOKEN;
		}
	}
}