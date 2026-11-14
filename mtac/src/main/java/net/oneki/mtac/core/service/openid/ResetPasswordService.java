package net.oneki.mtac.core.service.openid;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.oidc.OidcGrantRevocation;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.dto.SuccessErrorResponse;
import net.oneki.mtac.model.core.dto.SuccessErrorResponse.Status;
import net.oneki.mtac.model.core.openid.ResetPasswordRequest;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResetPasswordService {
  /** Shortest password accepted when a password is chosen with a reset link. */
  public static final int PASSWORD_MIN_LENGTH = 8;
  /** Longest password accepted, in UTF-8 bytes: BCrypt ignores (or refuses) anything beyond. */
  public static final int PASSWORD_MAX_BYTES = 72;
  /** Claim of a set-password link telling what it was sent for: {@link #PURPOSE_INVITATION} or {@link #PURPOSE_RESET}. */
  public static final String PURPOSE_CLAIM = "purpose";
  /** Link of an invitation (first password of a new account), valid {@code mtac.jwt.invitation-expiration-sec}. */
  public static final String PURPOSE_INVITATION = "invitation";
  /** Link of a "forgot password" or of an administrator, valid {@code mtac.jwt.reset-password-expiration-sec}. */
  public static final String PURPOSE_RESET = "reset";

  protected MtacProperties mtacProperties;
  protected DefaultUserService userService;
  protected JwtTokenService tokenService;
  protected OidcGrantRevocation grantRevocation;

  @Autowired
  public void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public void setUserService(DefaultUserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setGrantRevocation(OidcGrantRevocation grantRevocation) {
    this.grantRevocation = grantRevocation;
  }

  protected OidcGrantRevocation requireGrantRevocation() {
    if (grantRevocation == null) {
      throw new IllegalStateException("OidcGrantRevocation is not configured: credentials cannot change");
    }
    return grantRevocation;
  }

  public void triggerResetPassword(String email) {
    triggerResetPassword(email, getResetLinkSender());
  }

  /**
   * Same as {@link #triggerResetPassword(String)}, the link sent also carries
   * the given query parameters (for instance the context of the hosted pages
   * of the OpenID provider, to resume a sign-in once the password is changed).
   */
  public void triggerResetPassword(String email, Map<String, String> linkParams) {
    BiConsumer<String, User> sender = getResetLinkSender();
    triggerResetPassword(email, (link, user) -> sender.accept(appendParams(link, linkParams), user));
  }

  private static String appendParams(String link, Map<String, String> params) {
    if (params == null || params.isEmpty()) {
      return link;
    }
    var result = new StringBuilder(link);
    for (var param : params.entrySet()) {
      if (param.getValue() == null) {
        continue;
      }
      result.append(result.indexOf("?") < 0 ? '?' : '&')
          .append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8)).append('=')
          .append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
    }
    return result.toString();
  }

  public void triggerResetPassword(String email, BiConsumer<String, User> resetLinkSender) {
    triggerResetPassword(email, mtacProperties.getJwt().getResetPasswordExpirationSec(), resetLinkSender);
  }

  public void triggerResetPassword(String email, Integer resetPasswordExpirationSec, BiConsumer<String, User> resetLinkSender) {

    User user = null;
    try {
      user = userService.getByUniqueLabelUnsecureOrReturnNull(email);
    } catch (Exception e) {
      // user not found, do nothing
      return;
    }

    if (user == null)
      return;
    if (UserService.isDisabled(user)) {
      // same answer as an unknown address: no link for a disabled user (it would be refused anyway)
      log.info("Reset password request ignored for the disabled user {}", user.getId());
      return;
    }

    if (user.getResetPasswordTokenIssuedAt() != null && user.getResetPasswordTokenIssuedAt().isAfter(Instant.now().minusSeconds(mtacProperties.getResetPassword().getRequestCooldownSec()))) {
      // to avoid spam, user can only request reset password once every X seconds, X is defined in mtac properties
      throw new BusinessException("RESET_PASSWORD_REQUEST_COOLDOWN", "A reset password request has already been made recently. Please wait before making another request.");
    }
    var resetLink = generateResetLink(user, email, resetPasswordExpirationSec, false);
    // var resetToken = UUID.randomUUID().toString();
    // // generete JWT token that contains the reset token
    // var jwtToken = tokenService.newToken(Map.of("resetToken", resetToken, "email", email), resetPasswordExpirationSec);
    // // update user attribute to store the reset token
    // user.setResetPasswordToken(resetToken);
    // user.setResetPasswordTokenIssuedAt(Instant.now());
    // userService.updateUnsecure(user);
    // if (!resetLink.contains("?"))
    //   resetLink += "?";
    // ;
    // resetLink += "resetToken=" + jwtToken;
    resetLinkSender.accept(resetLink, user);
  }

  /**
   * Set-password link of an invitation: valid {@code mtac.jwt.invitation-expiration-sec} (31 days by default),
   * single use like a reset link (the identifier stored on the user is the nonce: a newer link, an invitation
   * sent again or a "forgot password", replaces it, so older links stop working). The link carries the claim
   * {@value #PURPOSE_CLAIM} = {@value #PURPOSE_INVITATION}: the hosted page says "set up your account", and the
   * password chosen with it records {@code activatedAt}. Records {@code invitationSentAt}. The caller sends the
   * link (its own mail template).
   *
   * @throws BusinessException USER_DISABLED for a disabled user (his links are refused, enable him first)
   */
  public String generateInvitationLink(User user) {
    if (UserService.isDisabled(user)) {
      throw new BusinessException("USER_DISABLED", "The user is disabled: enable the account before inviting it");
    }
    user.setInvitationSentAt(Instant.now());
    return generateLink(user, user.getLabel(), mtacProperties.getJwt().getInvitationExpirationSec(), false,
        PURPOSE_INVITATION);
  }

  /** Reset link (claim {@value #PURPOSE_CLAIM} = {@value #PURPOSE_RESET}), see {@link #generateInvitationLink}. */
  public String generateResetLink(User user, String email, Integer resetPasswordExpirationSec, boolean extend) {
    return generateLink(user, email, resetPasswordExpirationSec, extend, PURPOSE_RESET);
  }

  protected String generateLink(User user, String email, Integer resetPasswordExpirationSec, boolean extend,
      String purpose) {
    var resetLink = mtacProperties.getResetPassword().getLink();
    var resetToken = UUID.randomUUID().toString();
    if (extend && user.getResetPasswordTokenIssuedAt() != null && user.getResetPasswordToken() != null && user.getResetPasswordTokenIssuedAt().isAfter(Instant.now().minusSeconds(resetPasswordExpirationSec))) {
      resetToken = user.getResetPasswordToken();
    }
    // generete JWT token that contains the reset token
    var jwtToken = tokenService.newToken(Map.of("resetToken", resetToken, "email", email, PURPOSE_CLAIM, purpose),
        resetPasswordExpirationSec);
    // update user attribute to store the reset token
    user.setResetPasswordToken(resetToken);
    user.setResetPasswordTokenIssuedAt(Instant.now());
    userService.updateUnsecure(user);
    if (!resetLink.contains("?"))
      resetLink += "?";
    ;
    resetLink += "resetToken=" + jwtToken;
    return resetLink;
  }

  /**
   * Checks a reset link: its signature, its expiration and that it is the last
   * link issued to the user (a link is used once: a reset, or a newer link,
   * replaces the identifier stored on the user).
   */
  public SuccessErrorResponse verifyResetToken(String resetToken) {
    if (resolveResetToken(resetToken) != null) {
      return SuccessErrorResponse.builder()
          .status(Status.Success)
          .message("Reset token is valid")
          .build();
    }
    return SuccessErrorResponse.builder()
        .status(Status.Error)
        .errorCode("INVALID_RESET_TOKEN")
        .message("Invalid reset token")
        .build();
  }

  /**
   * The user a reset link was issued to, or null when the link is invalid:
   * bad signature, no or past expiration, unknown user, an identifier that is
   * not the one stored on the user (already used, or replaced by a newer
   * link), or a disabled user (no password is set for an account that cannot
   * sign in). Nothing about the token is logged.
   */
  public User resolveResetToken(String resetToken) {
    if (resetToken == null || resetToken.isBlank()) {
      return null;
    }
    try {
      // the expiration is checked below: an expired link is a normal case, not an error to log
      var claims = tokenService.verifyIgnoringExpiration(resetToken);
      var expiration = claims.getExpiration();
      if (expiration == null || !expiration.toInstant().isAfter(Instant.now())) {
        return null;
      }
      var email = claims.get("email", String.class);
      var uuid = claims.get("resetToken", String.class);
      if (email == null || uuid == null) {
        return null;
      }
      var user = userService.getByUniqueLabelUnsecureOrReturnNull(email);
      if (user == null || user.getResetPasswordToken() == null || !user.getResetPasswordToken().equals(uuid)
          || UserService.isDisabled(user)) {
        return null;
      }
      return user;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * True when a set-password link (valid signature, expired or not) was sent
   * with an invitation ({@value #PURPOSE_CLAIM} = {@value #PURPOSE_INVITATION});
   * false for a reset link, a link issued before the claim existed, or an
   * unreadable one.
   */
  public boolean isInvitationLink(String resetToken) {
    if (resetToken == null || resetToken.isBlank()) {
      return false;
    }
    try {
      var claims = tokenService.verifyIgnoringExpiration(resetToken);
      return PURPOSE_INVITATION.equals(claims.get(PURPOSE_CLAIM, String.class));
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Rules of a password chosen with a reset link: not blank, at least
   * {@value #PASSWORD_MIN_LENGTH} characters and at most
   * {@value #PASSWORD_MAX_BYTES} bytes in UTF-8.
   *
   * @return the message to show, or null when the password is accepted
   */
  public static String passwordRuleError(String password) {
    if (password == null || password.isBlank()) {
      return "Enter a new password.";
    }
    if (password.codePointCount(0, password.length()) < PASSWORD_MIN_LENGTH) {
      return "The password must be at least " + PASSWORD_MIN_LENGTH + " characters long.";
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
      return "The password is too long: " + PASSWORD_MAX_BYTES
          + " characters at most (fewer with accented letters or symbols).";
    }
    return null;
  }

  /**
   * Sets the new password chosen with the reset link, then revokes every
   * delegated access of the user and his Console session
   * ({@link OidcGrantRevocation}), in the same transaction. A disabled user's
   * link is refused (INVALID_RESET_TOKEN). The first password of an invited
   * user records {@code activatedAt}, only when it is not set yet: with the
   * invitation link, or with a reset link while the invitation is pending (the
   * invitation link expired and the user asked a new one with "forgot
   * password").
   */
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    try {
      var claims = tokenService.verify(request.getResetToken());
      var email = claims.get("email", String.class);
      var resetToken = claims.get("resetToken", String.class);
      if (email == null || resetToken == null) {
        throw new BusinessException("INVALID_RESET_TOKEN", "Invalid reset token");
      }
      var user = userService.getByUniqueLabelUnsecureOrReturnNull(email);
      if (user == null) {
        throw new BusinessException("USER_NOT_FOUND", "User not found for the provided email");
      }
      if (user.getResetPasswordToken() == null || !user.getResetPasswordToken().equals(resetToken)
          || UserService.isDisabled(user)) {
        throw new BusinessException("INVALID_RESET_TOKEN", "Invalid reset token");
      }
      var ruleError = passwordRuleError(request.getNewPassword());
      if (ruleError != null) {
        throw new BusinessException("INVALID_PASSWORD", ruleError);
      }
      user.setPassword(PasswordUtil.hash(request.getNewPassword()));
      user.setResetPasswordToken(null);
      user.setResetPasswordTokenIssuedAt(null);
      if (user.getActivatedAt() == null
          && (PURPOSE_INVITATION.equals(claims.get(PURPOSE_CLAIM, String.class)) || user.getInvitationSentAt() != null)) {
        user.setActivatedAt(Instant.now());
      }
      userService.updateUnsecure(user);
      // same transaction: a failed revocation rolls the new password back
      requireGrantRevocation().revokeAll(user, OidcGrantRevocation.REASON_PASSWORD_RESET, false);

    } catch (Exception e) {
      throw new BusinessException("RESET_PASSWORD_FAILED", "Failed to reset password: " + e.getMessage());
    }
  }

  protected <U extends User> BiConsumer<String, U> getResetLinkSender() {
    return (resetLink, user) -> {
      // This method can be overridden to send the reset link via email or any other
      // means
      // For now, we just log it
      log.info(
          "Reset password link for user {}: {}. Please implement the method getResetLinkSender() to send the link to the user by mail",
          user.getLabel(), resetLink);
    };
  }

}
