package net.oneki.mtac.core.util.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.oidc.TokenRevocationService;
import net.oneki.mtac.core.service.security.DelegatedScopePolicy;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.TokenAudiences;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.AudienceEnforcement;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.security.DefaultOAuth2User;
import net.oneki.mtac.model.core.util.security.JwtAuthoritiesExtractor;
import net.oneki.mtac.resource.iam.identity.user.UserService;

/**
 * Authenticates the bearer access tokens of one resource server path.
 *
 * Audience rules, when {@code mtac.jwt.audiences} is configured:
 * <ul>
 * <li>an aud that is not one configured audience URL is always refused: MFA,
 * trusted device, OIDC login context, OIDC userinfo access token, ID token
 * (aud = client_id), several audiences;</li>
 * <li>an audience the path accepts (one key, or several such as console or
 * mobile on {@code /api/**}, see {@link TokenAudience#anyOf(String...)}) and
 * the header {@code typ: at+jwt}: accepted;</li>
 * <li>no aud, no {@code typ: at+jwt}, or the configured audience of another
 * path: accepted and logged with {@code audience-enforcement: log}, refused
 * (invalid_token) with {@code strict};</li>
 * <li>the mobile audience without {@code client_id}: always refused (the
 * provider issues it to first-party clients only, with their client_id).</li>
 * </ul>
 * Without configured audiences (or without {@link MtacProperties}), the former
 * MTAC behaviour applies: tokens without aud are accepted, any aud is refused.
 *
 * <p>
 * A token whose user ({@code sub}), SSO session ({@code sid}) or refresh token
 * family ({@code fam}) is in the revocation list is refused (invalid_token):
 * see {@link TokenRevocationService}.
 */
@Slf4j
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
	// key of mtac.jwt.audiences required on the path this manager serves, or several (TokenAudience.anyOf);
	// null = any configured one
	private String requiredAudience;
	private MtacProperties mtacProperties; // null = no audience configured
	private TokenRevocationService tokenRevocations; // null = no revocation list

	public JwtAuthenticationManager(JwtAuthoritiesExtractor jwtAuthoritiesExtractor, JwtDecoder jwtDecoder,
			TokenRegistry tokenRegistry, TokenRepository tokenRepository, OpenIdService openIdService,
			String requiredAudience, MtacProperties mtacProperties) {
		this(jwtAuthoritiesExtractor, jwtDecoder, tokenRegistry, tokenRepository, openIdService);
		this.requiredAudience = requiredAudience;
		this.mtacProperties = mtacProperties;
	}

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
			checkAudience(jwt, sub);
			checkRevocation(jwt, sub);
			var claims = principalAttributes(jwt, openIdService.userinfo(sub, false), audienceKey(jwt));
			Collection<GrantedAuthority> authorities = new ArrayList<>(); // TODO

			OAuth2User principal = new DefaultOAuth2User(authorities, claims, nameAttributeKey, accessToken, tokenRefreshed);
			return new OAuth2AuthenticationToken(principal, authorities, "default");
		} catch (JwtException failed) {
			OAuth2Error invalidToken = invalidToken(failed.getMessage());
			throw new OAuth2AuthenticationException(invalidToken, invalidToken.getDescription(), failed);
		}

	}

	/**
	 * Attributes of the principal: the userinfo claims of the subject. A token
	 * delegated to an OAuth client (it carries a {@code client_id} claim, RFC
	 * 9068) also brings its {@code client_id} and granted {@code scope}, which
	 * {@link DelegatedScopePolicy} uses to cap the rights of the user. Other
	 * tokens (Console, service accounts) keep the userinfo claims only.
	 *
	 * <p>
	 * The userinfo claims are cached and shared between requests: they are copied,
	 * never modified.
	 */
	public static Map<String, Object> principalAttributes(Jwt jwt, Map<String, Object> userinfo) {
		return principalAttributes(jwt, userinfo, null);
	}

	/**
	 * Same as {@link #principalAttributes(Jwt, Map)}; a delegated token also
	 * brings the key of its audience ({@link DelegatedScopePolicy#ATTR_AUDIENCE}):
	 * the mobile one makes it a first-party mobile token.
	 *
	 * @param audienceKey key of the configured audience of the token, null if none
	 */
	public static Map<String, Object> principalAttributes(Jwt jwt, Map<String, Object> userinfo,
			String audienceKey) {
		var clientId = jwt.getClaimAsString(DelegatedScopePolicy.ATTR_CLIENT_ID);
		var impersonation = jwt.getClaims().get("imp") != null;
		var consoleSession = jwt.getClaimAsString("csid");
		if ((clientId == null || clientId.isBlank()) && !impersonation && consoleSession == null) {
			return userinfo;
		}
		var attributes = new LinkedHashMap<String, Object>(userinfo);
		if (consoleSession != null) {
			// the Console session of the token (ConsoleSessionService): logout ends it, a password change keeps it
			attributes.put("csid", consoleSession);
		}
		if (impersonation) {
			// an impersonation token (ImpersonateService): the checks that refuse an action while impersonating read it
			attributes.put("imp", jwt.getClaims().get("imp"));
			attributes.put("onbehalf", jwt.getClaimAsString("onbehalf"));
		}
		if (clientId == null || clientId.isBlank()) {
			return attributes;
		}
		attributes.put(DelegatedScopePolicy.ATTR_CLIENT_ID, clientId);
		var scope = jwt.getClaimAsString(DelegatedScopePolicy.ATTR_SCOPE);
		attributes.put(DelegatedScopePolicy.ATTR_SCOPE, scope != null ? scope : "");
		// from the signed aud claim only, never from the userinfo claims
		attributes.remove(DelegatedScopePolicy.ATTR_AUDIENCE);
		if (audienceKey != null) {
			attributes.put(DelegatedScopePolicy.ATTR_AUDIENCE, audienceKey);
		}
		return attributes;
	}

	/** Key of the single configured audience of the token, null when there is none or no audience is configured. */
	private String audienceKey(Jwt jwt) {
		if (mtacProperties == null) {
			return null;
		}
		var audience = jwt.getAudience();
		if (audience == null || audience.size() != 1) {
			return null;
		}
		return new TokenAudiences(mtacProperties).keyOf(audience.get(0)).orElse(null);
	}

	/**
	 * Validator for the decoders of the resource server: the Spring defaults
	 * (timestamps, X.509 thumbprint) but the typ header may be {@code JWT},
	 * {@code at+jwt} or absent. The default one of Spring Security 7 refuses
	 * {@code at+jwt}; the audience and typ rules are then applied by
	 * {@link #checkAudience(Jwt, String)}.
	 */
	public static OAuth2TokenValidator<Jwt> accessTokenValidator() {
		var typeValidator = new JwtTypeValidator("JWT", TokenAudience.ACCESS_TOKEN_TYP);
		typeValidator.setAllowEmpty(true);
		return JwtValidators.createDefaultWithValidators(List.<OAuth2TokenValidator<Jwt>>of(typeValidator));
	}

	/**
	 * See the class comment.
	 *
	 * @throws JwtException when the token must be refused
	 */
	protected void checkAudience(Jwt jwt, String sub) {
		List<String> audience = jwt.getAudience();
		var hasAudience = audience != null && !audience.isEmpty();
		var tokenAudiences = mtacProperties != null ? new TokenAudiences(mtacProperties) : null;
		if (tokenAudiences == null || !tokenAudiences.isConfigured()) {
			// former behaviour: access tokens carry no audience. Every other token signed with
			// the same key (MFA step, trusted device, tokens issued to an OIDC client) is bound
			// to one and must never be accepted as a bearer.
			if (hasAudience) {
				throw new JwtException("The token was not issued to access this API");
			}
			return;
		}

		String audienceKey = null;
		if (hasAudience) {
			if (audience.size() != 1) {
				throw new JwtException("The token was not issued to access this API");
			}
			audienceKey = tokenAudiences.keyOf(audience.get(0)).orElse(null);
			if (audienceKey == null) {
				// mfa, mfa-trusted-device, login context, userinfo, client_id of an ID token...
				throw new JwtException("The token was not issued to access this API");
			}
		}
		if (TokenAudience.MOBILE.equals(audienceKey) && jwt.getClaimAsString("client_id") == null) {
			// the provider issues the mobile audience to first-party clients only, always with their client_id:
			// without it the token would escape the limits of DelegatedScopePolicy
			log.warn("Access token refused: mobile audience without client_id (sub={}, path={})", sub, currentPath());
			throw new JwtException("The token was not issued to access this API");
		}
		var typ = jwt.getHeaders() != null ? jwt.getHeaders().get("typ") : null;
		var typOk = TokenAudience.ACCESS_TOKEN_TYP.equals(typ);
		var audienceOk = audienceKey != null
				&& (requiredAudience == null || TokenAudience.accepts(requiredAudience, audienceKey));
		if (audienceOk && typOk) {
			return;
		}

		String reason;
		if (audienceKey == null) {
			reason = "no audience";
		} else if (!audienceOk) {
			reason = "audience " + audienceKey + " instead of " + requiredAudience;
		} else {
			reason = "no typ " + TokenAudience.ACCESS_TOKEN_TYP;
		}
		// A token delegated to an OAuth client (client_id claim) is always new: the log mode, which exists for the
		// tokens issued before the audiences, never applies to it. An MCP token only opens /mcp.
		var delegated = jwt.getClaimAsString("client_id") != null;
		if (delegated || mtacProperties.getJwt().getAudienceEnforcement() == AudienceEnforcement.strict) {
			log.warn("Access token refused: {} (sub={}, client_id={}, aud={}, typ={}, path={})", reason, sub,
					jwt.getClaimAsString("client_id"), audience, typ, currentPath());
			throw new JwtException("The token was not issued to access this API");
		}
		log.warn("Access token accepted while mtac.jwt.audience-enforcement is log, refused in strict mode: {} "
				+ "(sub={}, client_id={}, aud={}, typ={}, path={})", reason, sub, jwt.getClaimAsString("client_id"),
				audience, typ, currentPath());
	}

	/**
	 * Refuses a token whose user, SSO session or refresh token family is in the
	 * revocation list: a sign-out takes effect within about 30 seconds, even for
	 * long-lived access tokens. No database read: the list is held in memory.
	 *
	 * @throws JwtException when the token is revoked
	 */
	protected void checkRevocation(Jwt jwt, String sub) {
		if (tokenRevocations == null) {
			return;
		}
		var revoked = tokenRevocations.revokedClaim(sub, jwt.getClaimAsString("sid"), jwt.getClaimAsString("fam"),
				jwt.getIssuedAt());
		if (revoked != null) {
			log.info("Access token refused: its {} is revoked (sub={}, client_id={}, path={})", revoked.claim(), sub,
					jwt.getClaimAsString("client_id"), currentPath());
			throw new JwtException("The token has been revoked");
		}
	}

	/** Path of the current HTTP request, for the logs; null outside a request. */
	private static String currentPath() {
		var attributes = RequestContextHolder.getRequestAttributes();
		return attributes instanceof ServletRequestAttributes servletAttributes
				? servletAttributes.getRequest().getRequestURI()
				: null;
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