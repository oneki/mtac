package net.oneki.mtac.core.service.openid;

import java.util.HashMap;
import java.util.UUID;

import org.jboss.aerogear.security.otp.Totp;
import org.jboss.aerogear.security.otp.api.Base32;
import org.jboss.aerogear.security.otp.api.Clock;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.repository.TotpRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.TokenResponse;
import net.oneki.mtac.model.core.util.StringUtils;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;

@Service
@RequiredArgsConstructor
public class MfaService {
  private final JwtTokenService tokenService;
  private final DefaultUserService userService;
  private final Clock clock = new Clock();
  private final TotpRepository totpRepository;
  private final MtacProperties mtacProperties;

  public TokenResponse verifyTotp(String verificationCode, String mfaToken, Boolean trusted, String noMfaToken) {
    var claims = tokenService.verify(mfaToken);
    var audience = claims.getAudience();
    if (audience == null || !audience.contains("mfa")) {
      throw new BadCredentialsException(
          "Invalid audience. The provided authorization token is not a valid MFA authorization token");
    }
    var sub = claims.getSubject();
    var user = userService.getByUidUnsecure(sub);

    if (verificationCode == null) {
      // The user is using a trusted device, so we check the noMfaToken and not the
      // verification code
      if (noMfaToken == null) {
        throw new BadCredentialsException("Invalid verification code");
      }
      // check if the noMfaToken is valid (this token is used to not re-ask the
      // verification code up to x days)
      var noMfaClaims = tokenService.verify(noMfaToken);
      if (!noMfaClaims.getSubject().equals(claims.getSubject())) {
        throw new BadCredentialsException("The subject of the no_mfa_token is invalid");
      }

    } else if (verificationCode.length() == 8) {
      // it's a code sent by mail / sms

      String dbVerificationCode = user.getVerificationCode();
      if (dbVerificationCode == null) {
        throw new BadCredentialsException("The mail verification code has been revoked");
      }

      var dbVerificationCodeValue = dbVerificationCode.split(":")[0];
      var dbVerificationCodeExpiresAt = Long.valueOf(dbVerificationCode.split(":")[1]);

      if (dbVerificationCodeValue.equals(verificationCode)) {
        if (dbVerificationCodeExpiresAt < System.currentTimeMillis()) {
          throw new BadCredentialsException("This verification code has been expired");
        }
      } else {
        throw new BadCredentialsException("Invalid verification code");
      }
      user.setVerificationCode(null);
      userService.updateUnsecure(user);
    } else {
      // we received a TOTP code
      var totpSecret = user.getTotpSecret();
      if (totpSecret == null) {
        throw new RuntimeException("There is no TOTP secret defined for the user " + sub);
      }

      Totp totp = new Totp(totpSecret);

      if (!StringUtils.isStringLong(verificationCode) || !totp.verify(verificationCode)) {
        throw new BadCredentialsException("Invalid verification code");
      }

      if (isTotpRevoked(user.getId(), verificationCode)) {
        throw new BadCredentialsException("The verification code has already been used and is revoked");
      }

      // revoke totp
      revokeTotp(user.getId(), verificationCode);

      if (user.getMfaActive() == null || user.getMfaActive() == false) {
        user.setMfaActive(true);
        userService.updateUnsecure(user);
      }
    }
    var randomString = UUID.randomUUID().toString();
    var tokenResponse = TokenResponse.builder()
        .accessToken(tokenService.generateAccessToken(user.getUid(), user.getLabel()))
        .idToken(tokenService.generateIdToken(userService.userinfo(user.getUid(), true)))
        .refreshToken(tokenService.generateRefreshToken(user.getUid(), randomString))
        .expiresIn(mtacProperties.getJwt().getExpirationSec())
        .build();

    user.setRefreshToken(randomString);
    userService.updateUnsecure(user);

    if (trusted != null && trusted) {
      var noMfaExpirationSec = mtacProperties.getJwt().getRefreshExpirationSec();
      var noMfaClaims = new HashMap<String, Object>();
      noMfaClaims.put("sub", sub);
      noMfaClaims.put("aud", "mfa");
      noMfaToken = tokenService.newToken(noMfaClaims, noMfaExpirationSec);
      tokenResponse.setNoMfaToken(noMfaToken);
      tokenResponse.setNoMfaTokenExpiresIn(noMfaExpirationSec);
    }

    return tokenResponse;
  }

  public TokenResponse generateMfaTokenResponse(User user) {
    var totpSecret = user.getTotpSecret();

    if (totpSecret == null) {
      user.setTotpSecret(Base32.random());
      userService.updateUnsecure(user);
    }

    return TokenResponse.builder()
        .mfaToken(tokenService.generateMfaToken(user.getUid(), user.getLabel()))
        .mfaRequired(true)
        .mfaUser(user.getLabel())
        .mfaDelay(mtacProperties.getJwt().getMfaDelaySec())
        .totpSecret((user.getMfaActive() == null || user.getMfaActive() == false) ? user.getTotpSecret() : null)
        .build();
  }

  public boolean isTotpRevoked(int userId, String totp) {
    var currentInterval = clock.getCurrentInterval();
    return totpRepository.isTotpRevoked(userId, totp, currentInterval);
  }

  public void revokeTotp(int userId, String totp) {
    var currentInterval = clock.getCurrentInterval();
    totpRepository.revokeTotp(userId, totp, currentInterval);
  }
}
