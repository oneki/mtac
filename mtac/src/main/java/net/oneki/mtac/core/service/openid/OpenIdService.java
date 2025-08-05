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
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.application.Application;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.DefaultResourceService;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.identity.application.DefaultApplicationService;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;

@Service
@RequiredArgsConstructor
public class OpenIdService {
  private final DefaultUserService userService;
  private final DefaultApplicationService applicationService;
  private final PasswordUtil passwordUtil;
  private final JwtTokenService tokenService;
  private final MtacProperties mtacProperties;
  private final MfaService mfaService;
  private final TokenRegistry tokenRegistry;
  private final TokenRepository tokenRepository;
  private final SecurityContext securityContext;
  private final DefaultResourceService resourceService;

  public TokenResponse login(String username, String password, boolean consoleAccess, boolean refreshToken) {
    if (password == null || password.equals("")) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    }
    var identity = userService.getByUniqueLabelUnsecureOrReturnNull(username, Identity.class);
    if (identity == null) {
      throw new BusinessException("INVALID_CREDENTIALS", "incorrect credentials");
    }
    switch (identity) {
      case User user -> {
        return loginUser(user, password, refreshToken);
      }
      case Application application -> {
        return loginApplication(application, password);
      }
      default -> throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
    }
  }

  public TokenResponse loginApplication(Application application, String password) {
    if (!passwordUtil.matches(password, application.getPassword())) {
      throw new BusinessException("INVALID_CREDENTIALS", "client ID or client Secret incorrect");
    }
    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (application.getTokenExpiresIn() != null) {
      expirationSec = application.getTokenExpiresIn();
    }

    // store userinfo in cache
    tokenService.generateIdToken(applicationService.userinfo(application.getUid(), true));
    var tokenResponse = TokenResponse.builder()
        .accessToken(
            tokenService.generateAccessToken(application.getUid(), application.getLabel(), expirationSec, null))
        .expiresIn(expirationSec)
        .build();
    return tokenResponse;
  }

  public TokenResponse loginUser(User user, String password, boolean refreshToken) {
    if (!passwordUtil.matches(password, user.getPassword())) {
      throw new BusinessException("INVALID_CREDENTIALS", "Username or password incorrect");
    }
    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (user.getTokenExpiresIn() != null) {
      expirationSec = user.getTokenExpiresIn();
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
        .accessToken(tokenService.generateAccessToken(user.getUid(), user.getLabel(), expirationSec, null))
        .idToken(tokenService.generateIdToken(userService.userinfo(user.getUid(), true)))
        .expiresIn(expirationSec)
        .build();
    if (refreshToken == true) {
      tokenResponse.setRefreshToken(tokenService.generateRefreshToken(user.getUid(), randomString));
      user.setRefreshToken(randomString);
      userService.updateUnsecure(user);
    }

    return tokenResponse;
  }

  public Claims userinfo(String sub, boolean forceRefresh) {
    var userOrApplication = resourceService.getByUidUnsecure(sub);
    if (userOrApplication == null) {
      throw new BusinessException("INVALID_CREDENTIALS", "User or application not found");
    }
    switch(userOrApplication) {
      case User user -> {
        return userService.userinfo(user, forceRefresh);
      }
      case Application application -> {
        return applicationService.userinfo(application, forceRefresh);
      }
      default -> throw new BusinessException("INVALID_CREDENTIALS", "User or application not found");
    }
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

    var expirationSec = mtacProperties.getJwt().getExpirationSec();
    if (user.getTokenExpiresIn() != null) {
      expirationSec = user.getTokenExpiresIn();
    }
    return TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(sub, user.getLabel(), expirationSec, null))
        .expiresIn(expirationSec)
        .build();
  }

  public void logout() {
    tokenRegistry.remove(securityContext.getSubject());
    tokenRepository.deleteToken(securityContext.getSubject());
  }

}
