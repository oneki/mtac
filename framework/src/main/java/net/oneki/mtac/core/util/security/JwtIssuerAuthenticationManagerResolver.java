package net.oneki.mtac.core.util.security;

import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.util.Assert;

import com.nimbusds.jwt.JWTParser;

import net.oneki.mtac.core.util.security.BearerTokenResolver;
import net.oneki.mtac.core.util.security.JwtIssuerAuthenticationManagerResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;


public class JwtIssuerAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {
	private  AuthenticationManagerResolver<String> issuerAuthenticationManagerResolver;
	private  Converter<HttpServletRequest, String> issuerConverter = new JwtClaimIssuerConverter();

	
	/**
	 * Construct a {@link JwtIssuerAuthenticationManagerResolver} using the provided parameters
	 *
	 * Note that the {@link AuthenticationManagerResolver} provided in this constructor will need to
	 * verify that the issuer is trusted. This should be done via a whitelist.
	 *
	 * One way to achieve this is with a {@link Map} where the keys are the known issuers:
	 * <pre>
	 *     Map&lt;String, AuthenticationManager&gt; authenticationManagers = new HashMap&lt;&gt;();
	 *     authenticationManagers.put("https://issuerOne.example.org", managerOne);
	 *     authenticationManagers.put("https://issuerTwo.example.org", managerTwo);
	 *     JwtAuthenticationManagerResolver resolver = new JwtAuthenticationManagerResolver
	 *     	(authenticationManagers::get);
	 * </pre>
	 *
	 * The keys in the {@link Map} are the whitelist.
	 *
	 * @param issuerAuthenticationManagerResolver a strategy for resolving the {@link AuthenticationManager} by the issuer
	 */
	public JwtIssuerAuthenticationManagerResolver(AuthenticationManagerResolver<String> issuerAuthenticationManagerResolver) {
		Assert.notNull(issuerAuthenticationManagerResolver, "issuerAuthenticationManagerResolver cannot be null");
		this.issuerAuthenticationManagerResolver = issuerAuthenticationManagerResolver;
	}

    public JwtIssuerAuthenticationManagerResolver(AuthenticationManagerResolver<String> issuerAuthenticationManagerResolver, Converter<HttpServletRequest, String> issuerConverter) {
		Assert.notNull(issuerAuthenticationManagerResolver, "issuerAuthenticationManagerResolver cannot be null");
		this.issuerAuthenticationManagerResolver = issuerAuthenticationManagerResolver;
        if (issuerConverter != null) this.issuerConverter = issuerConverter;
	}

	/**
	 * Return an {@link AuthenticationManager} based off of the `iss` claim found in the request's bearer token
	 *
	 * @throws OAuth2AuthenticationException if the bearer token is malformed or an {@link AuthenticationManager}
	 * can't be derived from the issuer
	 */
	@Override
	public AuthenticationManager resolve(HttpServletRequest request) {
		String issuer = this.issuerConverter.convert(request);
		AuthenticationManager authenticationManager = this.issuerAuthenticationManagerResolver.resolve(issuer);
		if (authenticationManager == null) {
			throw new InvalidBearerTokenException("Invalid issuer");
		}
		return authenticationManager;
	}

	private static class JwtClaimIssuerConverter
			implements Converter<HttpServletRequest, String> {


		@Override
		public String convert(@NonNull HttpServletRequest request) {
			TokenResolverOutput tokenResolverOutput = this.resolve(request);
			try {
                if (tokenResolverOutput.getIssuer() != null) {
                    return tokenResolverOutput.getIssuer();
                }

                String issuer = JWTParser.parse(tokenResolverOutput.getToken()).getJWTClaimsSet().getIssuer();
				if (issuer != null) {
					return issuer;
				}
			} catch (Exception e) {
				throw new InvalidBearerTokenException(e.getMessage(), e);
			}
			throw new InvalidBearerTokenException("Missing issuer");
		}

        private TokenResolverOutput resolve(HttpServletRequest request) {
            org.springframework.security.oauth2.server.resource.web.BearerTokenResolver resolver = new BearerTokenResolver();
            String token = resolver.resolve(request);
            String issuer = request.getHeader("X-Token-Issuer");
            return new TokenResolverOutput(token, issuer);
        }
	}

    @Data
    @AllArgsConstructor
    private static class TokenResolverOutput {
        private String token;
        private String issuer;
    }
}