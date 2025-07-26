package net.oneki.mtac.core.service.openid;

import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.TokenResponse;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;

@Service
@RequiredArgsConstructor
public class OpenIdService {
  private final DefaultUserService userService;
  private final PasswordUtil passwordUtil;
  private final JwtTokenService tokenService;
  private final MtacProperties mtacProperties;
  private final MfaService mfaService;
  private final TokenRegistry tokenRegistry;
  private final TokenRepository tokenRepository;
  private final SecurityContext securityContext;

  public TokenResponse login(String username, String password) {
    if (password == null || password.equals("")) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }
    var user = userService.getByUniqueLabelUnsecureOrReturnNull(username);
    if (user == null || !passwordUtil.matches(password, user.getPassword())) {
      throw new BusinessException("INVALID_CREDENTIALS", "User/Password incorrect");
    }

    // Check if 2FA is required.
    // If so, return a response with a token of type MFA
    var isMfaRequired = user.getMfa();
    if (isMfaRequired != null && isMfaRequired == true) {
      return mfaService.generateMfaTokenResponse(user);
    }

    // If not, generate a token response with access, id and refresh tokens
    var randomString = UUID.randomUUID().toString();
    var tokenResponse = TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(user.getUid(), user.getLabel()))
        .idToken(tokenService.generateIdToken(userService.userinfo(user.getUid(), true)))
        .refreshToken(tokenService.generateRefreshToken(user.getUid(), randomString))
        .expiresIn(mtacProperties.getJwt().getExpirationSec())
        .build();

    user.setRefreshToken(randomString);
    userService.updateUnsecure(user);

    return tokenResponse;
  }

  public TokenResponse refreshToken(String refreshToken) {
    // decrypt the refresh token
    refreshToken = passwordUtil.decrypt(refreshToken);
    var parts = refreshToken.split(":");
    if (parts.length != 3) {
      throw new BadCredentialsException("Invalid refresh token format");
    }
    var sub = parts[0];
    var expirationTimestamp = Long.parseLong(parts[1]);
    var randomUUID = parts[2];

    if (expirationTimestamp < System.currentTimeMillis() / 1000) {
      throw new BadCredentialsException("Refresh token has expired");
    }

    var user = userService.getByUidUnsecure(sub);
    if (user == null) {
      throw new BadCredentialsException("User not found for the provided refresh token");
    }

    if (!user.getRefreshToken().equals(randomUUID)) {
      throw new BadCredentialsException("Invalid refresh token");
    }

    return TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(sub, user.getLabel()))
        .expiresIn(mtacProperties.getJwt().getExpirationSec())
        .build();
  }

  public void logout() {
    tokenRegistry.remove(securityContext.getSubject());
    tokenRepository.deleteToken(securityContext.getSubject());
  }

}
