package net.oneki.mtac.framework.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.security.otp.Totp;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Clock;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.TotpRepository;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;

@Service
@Slf4j
public class JwtTokenService implements Clock {
	public static final String kid = "sign1";
	private static final String DOT = ".";

	private String issuer;
	private int expirationSec;
	private int clockSkewSec;
	private String secretKey;
	private PrivateKeyEntry privateKeyEntry;
	private JWK jwk;
	private org.jboss.aerogear.security.otp.api.Clock clock;
	@Autowired
	TotpRepository totpRepository;

	JwtTokenService(@Value("${jwt.issuer:mtac}") final String issuer,
			@Value("${jwt.expiration-sec:86400}") final int expirationSec,
			@Value("${jwt.clock-skew-sec:300}") final int clockSkewSec,
			@Value("${jwt.secret:secret}") final String secret,
			@Value("${jwt.keystore.path}") final String keystorePath,
			@Value("${jwt.keystore.key.alias}") final String keyAlias) throws IOException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException {
		super();
		this.issuer = issuer;
		this.expirationSec = expirationSec;
		this.clockSkewSec = clockSkewSec;
		this.secretKey = Base64.getEncoder().encodeToString(secret.getBytes("UTF-8"));

		Resource resource = new ClassPathResource(keystorePath, JwtTokenService.class);
		try (InputStream is = resource.getInputStream()) {
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			keystore.load(is, secret.toCharArray());
			ProtectionParameter entryPassword = new KeyStore.PasswordProtection(secret.toCharArray());
			this.privateKeyEntry = (PrivateKeyEntry) keystore.getEntry(keyAlias, entryPassword);
			if (this.privateKeyEntry == null) {
				log.error("privateKeyEntry is null, this is not normal. Please verify the jwt.jks");
			}
		}
		this.jwk = jwk();
		this.clock = new org.jboss.aerogear.security.otp.api.Clock();

	}

	public String generatePermanentToken(final Map<String, Object> attributes) {
		return newToken(attributes, 0);
	}

	public Map<String, Object> getJwkAsJsonObject() {
		return jwk.toPublicJWK().toJSONObject();
	}

	private JWK jwk() {
		// @formatter:off
        try {
            var privateKey = (RSAPrivateKey) this.privateKeyEntry.getPrivateKey();
            var publicKey = (RSAPublicKey) this.privateKeyEntry.getCertificate().getPublicKey();
            return new RSAKey
                    .Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(kid)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        // @formatter:on
	}

	public Map<String, Object> generateExpiringToken(
			final net.oneki.mtac.model.core.util.security.Claims accessTokenClaims,
			final net.oneki.mtac.model.core.util.security.Claims idTokenClaims) {
		Map<String, Object> result = new HashMap<>();
		result.put("access_token", newToken(accessTokenClaims, expirationSec));
		if (idTokenClaims != null) {
			result.put("id_token", newToken(idTokenClaims, expirationSec));
		}
		result.put("token_type", "Bearer");
		result.put("expires_in", expirationSec);
		return result;
	}

	public Map<String, Object> generateMfaToken(String sub, String username, String totpSecret, Integer delay) {

		if (sub == null) {
			throw new UnexpectedException("An username is required to generate a MFA token");
		}

		if (delay == null) {
			delay = Constants.MFA_DEFAULT_DELAY;
		}

		Map<String, Object> result = new HashMap<>();

		var claims = new HashMap<String, Object>();
		claims.put("sub", sub);
		claims.put("aud", "mfa");
		var mfaToken = newToken(claims, 600);

		result.put("mfa_token", mfaToken);
		result.put("token_type", "Bearer");
		result.put("expires_in", 600);
		result.put("mfa_user", username);
		result.put("mfa_required", true);
		result.put("mfa_delay", delay);
		result.put("totp_secret", totpSecret);
		return result;
	}

	public boolean isTotpRevoked(int userId, String totp) {
		var currentInterval = clock.getCurrentInterval();
		return totpRepository.isTotpRevoked(userId, totp, currentInterval);
	}

	public void revokeTotp(int userId, String totp) {
		var currentInterval = clock.getCurrentInterval();
		totpRepository.revokeTotp(userId, totp, currentInterval);
	}

	public String newToken(final Map<String, Object> attributes) {
		return newToken(attributes, expirationSec);
	}

	public String newToken(final Map<String, Object> attributes, final int expiresInSec) {
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		final Claims claims = Jwts.claims().setIssuer(issuer).setIssuedAt(now.toDate());

		if (expiresInSec > 0) {
			final DateTime expiresAt = now.plusSeconds(expiresInSec);
			claims.setExpiration(expiresAt.toDate());
		}
		claims.putAll(attributes);

		return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.RS256, privateKeyEntry.getPrivateKey())
				/* .compressWith(COMPRESSION_CODEC) */.compact();
	}

	public Claims verify(final String token) {
		try {
			final JwtParser parser = Jwts.parser().requireIssuer(issuer).setClock(this)
					.setAllowedClockSkewSeconds(clockSkewSec).setSigningKey(privateKeyEntry.getCertificate().getPublicKey());
			return parser.parseClaimsJws(token).getBody();
		} catch (Exception e) {
		}

		try {
			final JwtParser parser = Jwts.parser().requireIssuer(issuer).setClock(this)
					.setAllowedClockSkewSeconds(clockSkewSec).setSigningKey(secretKey);
			return parser.parseClaimsJws(token).getBody();
		} catch (Exception e) {
			log.error("Invalid token", e);
			throw new BadCredentialsException("Invalid token");
		}

	}

	public Claims untrusted(final String token) {
		final JwtParser parser = Jwts.parser().requireIssuer(issuer).setClock(this)
				.setAllowedClockSkewSeconds(clockSkewSec);

		// See: https://github.com/jwtk/jjwt/issues/135
		final String withoutSignature = StringUtils.substringBeforeLast(token, DOT) + DOT;
		return parser.parseClaimsJwt(withoutSignature).getBody();
	}

	@Override
	public Date now() {
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		return now.toDate();
	}
}
