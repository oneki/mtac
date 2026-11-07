package net.oneki.mtac.core.service.openid;

import java.util.HashMap;
import java.util.UUID;

import org.jboss.aerogear.security.otp.Totp;
import org.jboss.aerogear.security.otp.api.Base32;
import org.jboss.aerogear.security.otp.api.Clock;
import org.springframework.beans.factory.annotation.Autowired;
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
import net.oneki.mtac.resource.iam.identity.user.UserService;

@Service
@RequiredArgsConstructor
public class MfaService {
  /** Audience of the short-lived token handed out between the password and the second factor. */
  public static final String MFA_AUDIENCE = "mfa";
  /** Audience of the long-lived token that lets a trusted device skip the second factor. */
  public static final String TRUSTED_DEVICE_AUDIENCE = "mfa-trusted-device";

  protected JwtTokenService tokenService;
  protected DefaultUserService userService;
  private final Clock clock = new Clock();
  protected TotpRepository totpRepository;
  protected MtacProperties mtacProperties;

  public TokenResponse verifyTotp(String verificationCode, String mfaToken, Boolean trusted, String noMfaToken) {
    var claims = getTokenService().verify(mfaToken);
    if (!MFA_AUDIENCE.equals(claims.getAudience())) {
      throw new BadCredentialsException(
          "Invalid audience. The provided authorization token is not a valid MFA authorization token");
    }
    var sub = claims.getSubject();
    var user = getUserService().getByUidUnsecure(sub);

    if (verificationCode == null) {
      // The user is using a trusted device, so we check the noMfaToken and not the
      // verification code
      if (noMfaToken == null) {
        throw new BadCredentialsException("Invalid verification code");
      }
      // check if the noMfaToken is valid (this token is used to not re-ask the
      // verification code up to x days)
      var noMfaClaims = getTokenService().verify(noMfaToken);
      if (!TRUSTED_DEVICE_AUDIENCE.equals(noMfaClaims.getAudience())) {
        throw new BadCredentialsException("The provided no_mfa_token is not a trusted device token");
      }
      if (!noMfaClaims.getSubject().equals(claims.getSubject())) {
        throw new BadCredentialsException("The subject of the no_mfa_token is invalid");
      }

    } else {
      verifyCode(user, verificationCode);
    }
    var randomString = UUID.randomUUID().toString();
    var tokenResponse = TokenResponse.builder()
        .accessToken(getTokenService().generateAccessToken(user.getUid(), user.getLabel()))
        .idToken(getTokenService().generateIdToken(getUserService().userinfo(user.getUid(), true)))
        .refreshToken(getTokenService().generateRefreshToken(user.getUid(), randomString))
        .expiresIn(getMtacProperties().getJwt().getExpirationSec())
        .build();

    user.setRefreshToken(randomString);
    getUserService().updateUnsecure(user);

    if (trusted != null && trusted) {
      var noMfaExpirationSec = getMtacProperties().getJwt().getRefreshExpirationSec();
      var noMfaClaims = new HashMap<String, Object>();
      noMfaClaims.put("sub", sub);
      noMfaClaims.put("aud", TRUSTED_DEVICE_AUDIENCE);
      noMfaToken = getTokenService().newToken(noMfaClaims, noMfaExpirationSec);
      tokenResponse.setNoMfaToken(noMfaToken);
      tokenResponse.setNoMfaTokenExpiresIn(noMfaExpirationSec);
    }

    return tokenResponse;
  }

  /**
   * User a MFA token was issued for, i.e. the user who passed the password step.
   *
   * @throws BadCredentialsException if the token is not a valid MFA token
   */
  public User getMfaUser(String mfaToken) {
    var claims = getTokenService().verify(mfaToken);
    if (!MFA_AUDIENCE.equals(claims.getAudience())) {
      throw new BadCredentialsException(
          "Invalid audience. The provided authorization token is not a valid MFA authorization token");
    }
    var user = getUserService().getByUidUnsecure(claims.getSubject());
    if (user == null) {
      throw new BadCredentialsException("Invalid MFA token");
    }
    return user;
  }

  /**
   * Checks a second-factor code for a user: either the 8 character code sent by
   * mail / sms, or a TOTP code (which can be used only once).
   *
   * @throws BadCredentialsException if the code is wrong, expired or already used
   */
  public void verifyCode(User user, String verificationCode) {
    if (verificationCode == null) {
      throw new BadCredentialsException("Invalid verification code");
    }
    if (verificationCode.length() == 8) {
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
      getUserService().updateUnsecure(user);
    } else {
      // we received a TOTP code
      var totpSecret = user.getTotpSecret();
      if (totpSecret == null) {
        throw new RuntimeException("There is no TOTP secret defined for the user " + user.getUid());
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
        getUserService().updateUnsecure(user);
      }
    }
  }

  public TokenResponse generateMfaTokenResponse(User user) {
    var totpSecret = user.getTotpSecret();

    if (totpSecret == null) {
      user.setTotpSecret(Base32.random());
      getUserService().updateUnsecure(user);
    }

    return TokenResponse.builder()
        .mfaToken(getTokenService().generateMfaToken(user.getUid(), user.getLabel()))
        .mfaRequired(true)
        .mfaUser(user.getLabel())
        .mfaDelay(getMtacProperties().getJwt().getMfaDelaySec())
        .totpSecret((user.getMfaActive() == null || user.getMfaActive() == false) ? user.getTotpSecret() : null)
        .build();
  }

  public boolean isTotpRevoked(int userId, String totp) {
    var currentInterval = clock.getCurrentInterval();
    return getTotpRepository().isTotpRevoked(userId, totp, currentInterval);
  }

  public void revokeTotp(int userId, String totp) {
    var currentInterval = clock.getCurrentInterval();
    getTotpRepository().revokeTotp(userId, totp, currentInterval);
  }

  public JwtTokenService getTokenService() {
    return tokenService;
  }

  public UserService<?, ? super User> getUserService() {
    return userService;
  }

  public TotpRepository getTotpRepository() {
    return totpRepository;
  }

  public MtacProperties getMtacProperties() {
    return mtacProperties;
  }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setUserService(DefaultUserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setTotpRepository(TotpRepository totpRepository) {
    this.totpRepository = totpRepository;
  }

  @Autowired
  public void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }
}
