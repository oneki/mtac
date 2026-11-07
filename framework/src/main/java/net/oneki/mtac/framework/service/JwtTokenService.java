package net.oneki.mtac.framework.service;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.jsonwebtoken.Clock;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.core.util.security.Claims;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtTokenService implements Clock {
	public static final String kid = "sign1";
	private static final String DOT = ".";
	private final MtacProperties mtacProperties;

	private PrivateKeyEntry privateKeyEntry;
	private JWK jwk;

	// Access token
	/**
	 * Access token without audience: the former MTAC behaviour, kept for the
	 * applications that configure no {@code mtac.jwt.audiences}. Prefer the
	 * variants taking an audience key.
	 */
	public String generateAccessToken(String sub) {
		return generateAccessToken(sub, null);
	}

	/** See {@link #generateAccessToken(String)}: no audience. */
	public String generateAccessToken(String sub, String username) {
		return generateAccessToken(sub, username, null, null);
	}

	/** See {@link #generateAccessToken(String)}: no audience. */
	public String generateAccessToken(String sub, String username, Integer expirationSec, Map<String, Object> additionalClaims) {
		return accessToken(sub, username, expirationSec, additionalClaims, null);
	}

	/**
	 * Access token for one audience.
	 *
	 * @param audience key of {@code mtac.jwt.audiences} (see
	 *                 {@link net.oneki.mtac.model.core.openid.oidc.TokenAudience}),
	 *                 written in the aud claim as its configured URL. Ignored while
	 *                 no audience is configured (former MTAC behaviour); otherwise
	 *                 an unknown key is an {@link IllegalArgumentException}.
	 */
	public String generateAccessToken(String sub, String username, String audience) {
		return generateAccessToken(sub, username, null, null, audience);
	}

	public String generateAccessToken(String sub, String username, Integer expirationSec, Map<String, Object> additionalClaims,
			String audience) {
		return accessToken(sub, username, expirationSec, additionalClaims, audienceUrl(audience));
	}

	private String accessToken(String sub, String username, Integer expirationSec, Map<String, Object> additionalClaims,
			String audienceUrl) {
		if (expirationSec == null) {
			expirationSec = mtacProperties.getJwt().getExpirationSec();
		}
		var claims = new Claims();
		claims.put("jti", UUID.randomUUID().toString());
		claims.put("sub", sub);
		if (username != null) {
			claims.put("username", username);
		}
		if (additionalClaims != null) {
			claims.putAll(additionalClaims);
		}
		if (audienceUrl != null) {
			claims.put("aud", audienceUrl);
		}
		return newAccessToken(claims, expirationSec);
	}

	/**
	 * URL of an audience key, or null when no audience is configured.
	 *
	 * @throws IllegalArgumentException if audiences are configured and the key is
	 *                                  missing or not one of them
	 */
	public String audienceUrl(String audience) {
		var audiences = new TokenAudiences(mtacProperties);
		if (!audiences.isConfigured()) {
			return null;
		}
		var url = audience != null ? audiences.url(audience) : null;
		if (url == null) {
			throw new IllegalArgumentException("Unknown access token audience: " + audience
					+ " (expected a key of mtac.jwt.audiences)");
		}
		return url;
	}

	/**
	 * Signs an access token: same as {@link #newToken(Map, int)} plus the JOSE
	 * header {@code typ: at+jwt} (RFC 9068), which no other token signed with
	 * this key carries (ID, MFA, trusted device, reset link, login context).
	 */
	public String newAccessToken(final Map<String, Object> attributes, final int expiresInSec) {
		// without configured audiences MTAC keeps its former tokens: a decoder still on the Spring Security default
		// validator refuses the at+jwt type
		var typ = mtacProperties.getJwt().getAudiences().isEmpty() ? null : TokenAudience.ACCESS_TOKEN_TYP;
		return newToken(attributes, expiresInSec, typ);
	}

	// id token
	public String generateIdToken(Map<String, Object> claims) {
		return newToken(claims, mtacProperties.getJwt().getExpirationSec());
	}

	// Refresh token
	public String generateRefreshToken(String sub, String randomString) {
		var refreshToken = sub + ":" + Instant.now().plusSeconds(mtacProperties.getJwt().getRefreshExpirationSec()).getEpochSecond() + ":"
				+ randomString;
		refreshToken = PasswordUtil.encrypt(refreshToken);
		return refreshToken;
	}

	public String generatePermanentToken(final Map<String, Object> attributes) {
		return newToken(attributes, 0);
	}

	// public Map<String, Object> generateExpiringToken(
	// 		final net.oneki.mtac.model.core.util.security.Claims accessTokenClaims,
	// 		final net.oneki.mtac.model.core.util.security.Claims idTokenClaims) {
	// 	Map<String, Object> result = new HashMap<>();
	// 	result.put("access_token", newToken(accessTokenClaims, expirationSec));
	// 	if (idTokenClaims != null) {
	// 		result.put("id_token", newToken(idTokenClaims, expirationSec));
	// 	}
	// 	result.put("token_type", "Bearer");
	// 	result.put("expires_in", expirationSec);
	// 	return result;
	// }

	// MFA token
	public String generateMfaToken(String sub, String username) {

		if (sub == null) {
			throw new UnexpectedException("An username is required to generate a MFA token");
		}
		var claims = new HashMap<String, Object>();
		claims.put("sub", sub);
		claims.put("aud", "mfa");
		return newToken(claims, mtacProperties.getJwt().getExpirationSec());

		// result.put("mfa_token", mfaToken);
		// result.put("token_type", "Bearer");
		// result.put("expires_in", 600);
		// result.put("mfa_user", username);
		// result.put("mfa_required", true);
		// result.put("mfa_delay", delay);
		// result.put("totp_secret", totpSecret);
		// return result;
	}

	// public boolean isTotpRevoked(int userId, String totp) {
	// 	var currentInterval = clock.getCurrentInterval();
	// 	return totpRepository.isTotpRevoked(userId, totp, currentInterval);
	// }

	// public void revokeTotp(int userId, String totp) {
	// 	var currentInterval = clock.getCurrentInterval();
	// 	totpRepository.revokeTotp(userId, totp, currentInterval);
	// }

	public String newToken(final Map<String, Object> attributes) {
		return newToken(attributes, mtacProperties.getJwt().getExpirationSec());
	}

	public String newToken(final Map<String, Object> attributes, final int expiresInSec) {
		return newToken(attributes, expiresInSec, null);
	}

	private String newToken(final Map<String, Object> attributes, final int expiresInSec, final String typ) {
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		final io.jsonwebtoken.Claims claims = Jwts.claims().setIssuer(mtacProperties.getJwt().getIssuer()).setIssuedAt(now.toDate());

		if (expiresInSec > 0) {
			final DateTime expiresAt = now.plusSeconds(expiresInSec);
			claims.setExpiration(expiresAt.toDate());
		}
		claims.putAll(attributes);

		var keyAlias = mtacProperties.getJwt().getKeystoreKeyAlias();
		var builder = Jwts.builder().setHeaderParam("kid", keyAlias);
		if (typ != null) {
			builder.setHeaderParam("typ", typ);
		}
		return builder
			.setClaims(claims)
			.signWith(SignatureAlgorithm.RS256, getPrivateKeyEntry().getPrivateKey())
			.compact();

		// return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.RS256, getPrivateKeyEntry().getPrivateKey())
		// 		/* .compressWith(COMPRESSION_CODEC) */.compact();
	}

	public io.jsonwebtoken.Claims verify(final String token) {
		var config = mtacProperties.getJwt();
		io.jsonwebtoken.Claims claims = null;
		try {
			final JwtParser parser = Jwts.parser().setClock(this)
					.setAllowedClockSkewSeconds(config.getClockSkewSec()).setSigningKey( getPrivateKeyEntry().getCertificate().getPublicKey());
			claims = parser.parseClaimsJws(token).getBody();
		} catch (Exception e) {
		}

		if (claims == null) {
			try {
				final JwtParser parser = Jwts.parser().setClock(this)
						.setAllowedClockSkewSeconds(config.getClockSkewSec()).setSigningKey(config.getSecretKey());
				claims = parser.parseClaimsJws(token).getBody();
			} catch (Exception e) {
				log.error("Invalid token", e);
				throw new BadCredentialsException("Invalid token");
			}
		}

		if (!isAcceptedIssuer(claims.getIssuer())) {
			log.error("Invalid token: unexpected issuer {}", claims.getIssuer());
			throw new BadCredentialsException("Invalid token");
		}
		return claims;
	}

	/**
	 * Same as {@link #verify(String)} but an expired token is accepted, as long as
	 * its signature is valid. Meant for the {@code id_token_hint} of a logout,
	 * which only identifies the session to end.
	 */
	public io.jsonwebtoken.Claims verifyIgnoringExpiration(final String token) {
		var config = mtacProperties.getJwt();
		io.jsonwebtoken.Claims claims;
		try {
			final JwtParser parser = Jwts.parser().setClock(this)
					.setAllowedClockSkewSeconds(config.getClockSkewSec()).setSigningKey( getPrivateKeyEntry().getCertificate().getPublicKey());
			claims = parser.parseClaimsJws(token).getBody();
		} catch (io.jsonwebtoken.ExpiredJwtException e) {
			// the signature is checked before the expiration
			claims = e.getClaims();
		} catch (Exception e) {
			throw new BadCredentialsException("Invalid token");
		}
		if (!isAcceptedIssuer(claims.getIssuer())) {
			throw new BadCredentialsException("Invalid token");
		}
		return claims;
	}

	public io.jsonwebtoken.Claims untrusted(final String token) {
		var config = mtacProperties.getJwt();
		final JwtParser parser = Jwts.parser().setClock(this)
				.setAllowedClockSkewSeconds(config.getClockSkewSec());

		// See: https://github.com/jwtk/jjwt/issues/135
		final String withoutSignature = StringUtils.substringBeforeLast(token, DOT) + DOT;
		var claims = parser.parseClaimsJwt(withoutSignature).getBody();
		if (!isAcceptedIssuer(claims.getIssuer())) {
			throw new BadCredentialsException("Invalid token");
		}
		return claims;
	}

	/**
	 * A token is accepted when it was issued under the current issuer or under one
	 * of the former ones still listed in {@code mtac.jwt.accepted-issuers}, so that
	 * renaming the issuer does not invalidate the tokens already in circulation.
	 */
	public boolean isAcceptedIssuer(String issuer) {
		if (issuer == null) {
			return false;
		}
		var config = mtacProperties.getJwt();
		if (issuer.equals(config.getIssuer())) {
			return true;
		}
		var acceptedIssuers = config.getAcceptedIssuers();
		return acceptedIssuers != null && acceptedIssuers.contains(issuer);
	}

	public PrivateKeyEntry getPrivateKeyEntry() {
		if (privateKeyEntry == null) {
			var config = mtacProperties.getJwt();
			Resource resource = new ClassPathResource(config.getKeystorePath(), JwtTokenService.class);
			try (InputStream is = resource.getInputStream()) {
				KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
				keystore.load(is, config.getKeystorePassword().toCharArray());
				ProtectionParameter entryPassword = new KeyStore.PasswordProtection(config.getKeystorePassword().toCharArray());
				privateKeyEntry = (PrivateKeyEntry) keystore.getEntry(config.getKeystoreKeyAlias(), entryPassword);
				if (privateKeyEntry == null) {
					throw new UnexpectedException("privateKeyEntry is null, this is not normal. Please verify the jwt.jks");
				}
			} catch (Exception e) {
				log.error("Error loading private key entry from keystore", e);
				throw new UnexpectedException("Error loading private key entry from keystore: " + e.getMessage(), e);
			}
		}
		return privateKeyEntry;
	}

	public Map<String, Object> getJwkAsJsonObject() {
		return jwk().toPublicJWK().toJSONObject();
	}

	public JWK jwk() {
		if (jwk == null) {
			try {
				var privateKey = (RSAPrivateKey) getPrivateKeyEntry().getPrivateKey();
				var publicKey = (RSAPublicKey) getPrivateKeyEntry().getCertificate().getPublicKey();
				jwk = new RSAKey.Builder(publicKey)
						.privateKey(privateKey)
						.keyUse(KeyUse.SIGNATURE)
						.keyID(kid)
						.algorithm(JWSAlgorithm.RS256)
						.build();
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}
		return jwk;
	}

	@Override
	public Date now() {
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		return now.toDate();
	}
}
