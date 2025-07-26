package net.oneki.mtac.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.openid.MfaService;
import net.oneki.mtac.core.service.openid.OpenIdService;
import net.oneki.mtac.core.service.openid.ResetPasswordService;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.JwksResponse;
import net.oneki.mtac.model.core.openid.LoginRequest;
import net.oneki.mtac.model.core.openid.ResetPasswordRequest;
import net.oneki.mtac.model.core.openid.TokenResponse;
import net.oneki.mtac.model.core.openid.TriggerResetPasswordRequest;
import net.oneki.mtac.model.core.util.StringUtils;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@RequiredArgsConstructor
public abstract class OpenIdController {

  protected JwtTokenService tokenService;
  protected PermissionService permissionService;
  protected OpenIdService openIdService;
  protected ResetPasswordService resetPasswordService;
  protected MfaService mfaService;
  protected DefaultUserService userService;
  protected RequestMappingHandlerMapping handlerMapping;
  protected MtacProperties mtacProperties;

  @PostConstruct
  public void init() throws NoSuchMethodException {

    // Login (JSON)
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/oauth2/token")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("auth", LoginRequest.class));

    // login (url-encoded - OIDC standard)
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/oauth2/token")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("auth", String.class, String.class,
            String.class, String.class));

    // logout
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/logout")
            .methods(RequestMethod.POST)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("logout"));

    // TOTP verification
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/totp/verify")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("verifyTotp", Map.class, String.class));

    // JWKS endpoint
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/.well-known/jwks.json")
            .methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("getJwks"));

    // Userinfo endpoint
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/oauth2/userinfo")
            .methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("userinfo"));

    // Trigger reset password
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/trigger-reset-password")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("triggerResetpassword", TriggerResetPasswordRequest.class));

    // Verify reset password token
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/verify-reset-token")
            .methods(RequestMethod.GET)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("verifyResetPassword", String.class));

    // Reset password
    handlerMapping.registerMapping(
        RequestMappingInfo.paths(getApiBasePath() + "/login/reset-password")
            .methods(RequestMethod.POST)
            .consumes(MediaType.APPLICATION_JSON_VALUE)
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build(),
        this,
        OpenIdController.class.getDeclaredMethod("resetPassword", ResetPasswordRequest.class));
  }

  public ResponseEntity<TokenResponse> auth(@RequestBody LoginRequest request) throws Exception {
    var result = getOpenIdService().login(request.getClient_id(), request.getClient_secret());
    var tokenLocation = mtacProperties.getIam().getTokenLocation();
    var httpHeaders = new HttpHeaders();
    if ("cookie".equals(tokenLocation)) {
      var cookieName = mtacProperties.getIam().getCookieName();
      httpHeaders.add("Set-Cookie",
          cookieName + "=" + result.getAccessToken() + "; Path=/; SameSite=Strict; httpOnly");
    }
    return ResponseEntity.ok().headers(httpHeaders).body(result);
  }

  public ResponseEntity<TokenResponse> auth(
      @RequestParam(value = "client_id", required = true) String client_id,
      @RequestParam(value = "client_secret", required = false) String client_secret,
      @RequestParam(value = "grant_type", required = false) String grant_type,
      @RequestParam(value = "refresh_token", required = false) String refresh_token)
      throws Exception {
    TokenResponse result = null;
    if ("refresh_token".equals(grant_type)) {
      result = getOpenIdService().refreshToken(refresh_token);
    } else if ("client_credentials".equals(grant_type)) {
      result = getOpenIdService().login(client_id, client_secret);
    } else {
      throw new IllegalArgumentException("Unsupported grant_type: " + grant_type);
    }
    var tokenLocation = mtacProperties.getIam().getTokenLocation();
    var httpHeaders = new HttpHeaders();
    if ("cookie".equals(tokenLocation)) {
      var cookieName = mtacProperties.getIam().getCookieName();
      httpHeaders.add("Set-Cookie",
          cookieName + "=" + result.getAccessToken() + "; Path=/; SameSite=Strict; httpOnly");
    }

    return ResponseEntity.ok().headers(httpHeaders).body(result);
  }

  public Map<String, Object> userinfo() throws Exception {
    return getUserService().userinfo();
  }

  public ResponseEntity<Void> logout() throws Exception {
    getOpenIdService().logout();
    var tokenLocation = mtacProperties.getIam().getTokenLocation();
    var httpHeaders = new HttpHeaders();
    if ("cookie".equals(tokenLocation)) {
      cleanCookies(httpHeaders);
    }
    return ResponseEntity.ok().headers(httpHeaders).build();
  }

  public JwksResponse getJwks() {
    return JwksResponse.builder().key(tokenService.getJwkAsJsonObject()).build();
  }

  public ResponseEntity<?> verifyTotp(@RequestBody Map<String, String> body,
      @RequestHeader(value = "authorization", required = true) String authorizationHeader)
      throws Exception {
    String token = authorizationHeader.trim().substring(7);
    var result = getMfaService().verifyTotp(body.getOrDefault("totp", null), token,
        StringUtils.toBoolean(body.getOrDefault("trusted_device", null)),
        body.getOrDefault("no_mfa_token", null));
    var tokenLocation = mtacProperties.getIam().getTokenLocation();
    var httpHeaders = new HttpHeaders();
    if ("cookie".equals(tokenLocation)) {
      var cookieName = mtacProperties.getIam().getCookieName();
      httpHeaders.add("Set-Cookie",
          cookieName + "=" + result.getAccessToken() + "; Path=/; SameSite=Strict; httpOnly");
    }

    return ResponseEntity.ok().headers(httpHeaders).body(result);
  }

  public void triggerResetpassword(@RequestBody TriggerResetPasswordRequest request) {
    getResetPasswordService().triggerResetPassword(request.getEmail());
  }

  public void verifyResetPassword(@RequestParam("resetToken") String resetToken) {
    getResetPasswordService().verifyResetToken(resetToken);
  }

  public void resetPassword(@RequestBody ResetPasswordRequest request) {
    getResetPasswordService().resetPassword(request);
  }

  // @PostMapping(value = "/v1/totp/mail")
  // public Map<String, Object> sendVerificationCodeByMail(@RequestHeader(value =
  // "authorization", required = true) String authorizationHeader) throws
  // Exception {
  // String token = authorizationHeader.trim().substring(7);
  // getUserService().sendVerificationCodeByMail(token);
  // return Map.of("status", "success");
  // }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setPermissionService(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Autowired
  public void setOpenIdService(OpenIdService openIdService) {
    this.openIdService = openIdService;
  }

  @Autowired
  public void setResetPasswordService(ResetPasswordService resetPasswordService) {
    this.resetPasswordService = resetPasswordService;
  }

  @Autowired
  public void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public void setMfaService(MfaService mfaService) {
    this.mfaService = mfaService;
  }

  @Autowired
  public void setHandlerMapping(RequestMappingHandlerMapping handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  protected void cleanCookies(HttpHeaders httpHeaders) {
    var cookieName = mtacProperties.getIam().getCookieName();
    httpHeaders.add("Set-Cookie",
        cookieName + "=deleted; Path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Strict; httpOnly");
  }

  protected abstract UserService<?, ?> getUserService();

  protected OpenIdService getOpenIdService() {
    return openIdService;
  }

  protected MfaService getMfaService() {
    return mfaService;
  }

  protected ResetPasswordService getResetPasswordService() {
    return resetPasswordService;
  }

  protected String getApiBasePath() {
    return mtacProperties.getApiBasePath();
  }
}
