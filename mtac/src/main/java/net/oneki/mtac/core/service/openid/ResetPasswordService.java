package net.oneki.mtac.core.service.openid;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.dto.SuccessErrorResponse;
import net.oneki.mtac.model.core.dto.SuccessErrorResponse.Status;
import net.oneki.mtac.model.core.openid.ResetPasswordRequest;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.iam.identity.user.User;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResetPasswordService {
  protected MtacProperties mtacProperties;
  protected DefaultUserService userService;
  protected JwtTokenService tokenService;
  protected PasswordUtil passwordUtil;

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
  public void setPasswordUtil(PasswordUtil passwordUtil) {
    this.passwordUtil = passwordUtil;
  }

  public void triggerResetPassword(String email) {
    triggerResetPassword(email, getResetLinkSender());
  }

  public void triggerResetPassword(String email, BiConsumer<String, User> resetLinkSender) {
    var resetLink = mtacProperties.getResetPassword().getLink();
    User user = null;
    try {
      user = userService.getByUniqueLabelUnsecureOrReturnNull(email);
    } catch (Exception e) {
      // user not found, do nothing
      return;
    }

    if (user == null)
      return;
    var resetToken = UUID.randomUUID().toString();
    // generete JWT token that contains the reset token
    var jwtToken = tokenService.newToken(Map.of("resetToken", resetToken, "email", email),
        mtacProperties.getJwt().getResetPasswordExpirationSec());
    // update user attribute to store the reset token
    user.setResetPasswordToken(resetToken);
    userService.updateUnsecure(user);
    if (!resetLink.contains("?"))
      resetLink += "?";
    ;
    resetLink += "resetToken=" + jwtToken;
    resetLinkSender.accept(resetLink, user);
  }

  public SuccessErrorResponse verifyResetToken(String resetToken) {
    try {
      tokenService.verify(resetToken);
      return SuccessErrorResponse.builder()
          .status(Status.Success)
          .message("Reset token is valid")
          .build();
    } catch (Exception e) {
      return SuccessErrorResponse.builder()
          .status(Status.Error)
          .errorCode("INVALID_RESET_TOKEN")
          .message("Invalid reset token: " + e.getMessage())
          .build();
    }
  }

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
      if (user.getResetPasswordToken() == null || !user.getResetPasswordToken().equals(resetToken)) {
        throw new BusinessException("INVALID_RESET_TOKEN", "Invalid reset token");
      }
      user.setPassword(passwordUtil.hash(request.getNewPassword()));
      user.setResetPasswordToken(null);
      userService.updateUnsecure(user);

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
