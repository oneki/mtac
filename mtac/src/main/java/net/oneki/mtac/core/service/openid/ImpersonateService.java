package net.oneki.mtac.core.service.openid;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.model.core.openid.ImpersonateUserRequest;
import net.oneki.mtac.model.core.openid.ImpersonateUserResponse;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.resource.iam.identity.user.DefaultUserService;
import net.oneki.mtac.resource.iam.identity.user.UserService;

@Service
@RequiredArgsConstructor
public class ImpersonateService {
  protected PermissionService permissionService;
  protected SecurityContext securityContext;
  protected DefaultUserService userService;
  protected JwtTokenService tokenService;

  public ImpersonateUserResponse impersonateUser(ImpersonateUserRequest request) {
    if (getPermissionService().hasPermission(Resource.fromUid(request.getUserUid()), "impersonate")) {
      if (securityContext.getAttributes().containsKey("imp")) {
        throw new ForbiddenException("You are already impersonating a user.");
      }
      // generate a access token for the impersonated user
      var user = getUserService().getByUid(request.getUserUid());
      if (user == null) {
        throw new IllegalArgumentException("User not found: " + request.getUserUid());
      }
      var claims = new Claims();
      claims.put("jti", UUID.randomUUID().toString());
      var sub = Resource.toUid(user.getId());
      claims.put("sub", sub);
      claims.put("username", user.getLabel());
      claims.put("imp", true);
      claims.put("onbehalf", securityContext.getUsername());
      var accessToken = getTokenService().newToken(claims, 3600); // 1 hour expiration
      return ImpersonateUserResponse.builder()
          .accessToken(accessToken)
          .originalAccessToken(securityContext.getJwtToken())
          .build();
    } else {
      throw new ForbiddenException("You do not have permission to impersonate this user.");
    }
  }

  public PermissionService getPermissionService() {
    return permissionService;
  }

  public UserService<?,?> getUserService() {
    return userService;
  }

  public JwtTokenService getTokenService() {
    return tokenService;
  }

  @Autowired
  public void setPermissionService(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Autowired
  public void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }

  @Autowired
  public void setUserService(DefaultUserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }
}
