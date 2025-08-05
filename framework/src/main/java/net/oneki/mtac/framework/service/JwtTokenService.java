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
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.core.util.security.Claims;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtTokenService implements Clock {
	public static final String kid = "sign1";
	private static final String DOT = ".";
	private final MtacProperties mtacProperties;
	private final PasswordUtil passwordUtil;

	private PrivateKeyEntry privateKeyEntry;
	private JWK jwk;

	// Access token
	public String generateAccessToken(String sub) {
		return generateAccessToken(sub, null);
	}

	public String generateAccessToken(String sub, String username) {
		return generateAccessToken(sub, username, null, null);
	}

	public String generateAccessToken(String sub, String username, Integer expirationSec, Map<String, Object> additionalClaims) {
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
		return newToken(claims, expirationSec);
	}

	// id token
	public String generateIdToken(Map<String, Object> claims) {
		return newToken(claims, mtacProperties.getJwt().getExpirationSec());
	}

	// Refresh token
	public String generateRefreshToken(String sub, String randomString) {
		var refreshToken = sub + ":" + Instant.now().plusSeconds(mtacProperties.getJwt().getRefreshExpirationSec()).getEpochSecond() + ":"
				+ randomString;
		refreshToken = passwordUtil.encrypt(refreshToken);
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
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		final io.jsonwebtoken.Claims claims = Jwts.claims().setIssuer(mtacProperties.getJwt().getIssuer()).setIssuedAt(now.toDate());

		if (expiresInSec > 0) {
			final DateTime expiresAt = now.plusSeconds(expiresInSec);
			claims.setExpiration(expiresAt.toDate());
		}
		claims.putAll(attributes);

		return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.RS256, getPrivateKeyEntry().getPrivateKey())
				/* .compressWith(COMPRESSION_CODEC) */.compact();
	}

	public io.jsonwebtoken.Claims verify(final String token) {
		var config = mtacProperties.getJwt();
		try {
			final JwtParser parser = Jwts.parser().requireIssuer(config.getIssuer()).setClock(this)
					.setAllowedClockSkewSeconds(config.getClockSkewSec()).setSigningKey( getPrivateKeyEntry().getCertificate().getPublicKey());
			return parser.parseClaimsJws(token).getBody();
		} catch (Exception e) {
		}

		try {
			final JwtParser parser = Jwts.parser().requireIssuer(config.getIssuer()).setClock(this)
					.setAllowedClockSkewSeconds(config.getClockSkewSec()).setSigningKey(config.getSecretKey());
			return parser.parseClaimsJws(token).getBody();
		} catch (Exception e) {
			log.error("Invalid token", e);
			throw new BadCredentialsException("Invalid token");
		}

	}

	public io.jsonwebtoken.Claims untrusted(final String token) {
		var config = mtacProperties.getJwt();
		final JwtParser parser = Jwts.parser().requireIssuer(config.getIssuer()).setClock(this)
				.setAllowedClockSkewSeconds(config.getClockSkewSec());

		// See: https://github.com/jwtk/jjwt/issues/135
		final String withoutSignature = StringUtils.substringBeforeLast(token, DOT) + DOT;
		return parser.parseClaimsJwt(withoutSignature).getBody();
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
