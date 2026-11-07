package net.oneki.mtac.model.core.openid.oidc;

import lombok.Getter;

/**
 * Protocol error of the OpenID provider. {@code error} is one of the codes
 * defined by OAuth 2.0 / OpenID Connect (invalid_request, invalid_grant, ...).
 */
@Getter
public class OidcException extends RuntimeException {
  private final String error;
  private final int status;

  public OidcException(String error, String description) {
    this(error, description, 400);
  }

  public OidcException(String error, String description, int status) {
    super(description);
    this.error = error;
    this.status = status;
  }

  public static OidcException invalidRequest(String description) {
    return new OidcException("invalid_request", description);
  }

  public static OidcException invalidGrant(String description) {
    return new OidcException("invalid_grant", description);
  }

  public static OidcException invalidClient(String description) {
    return new OidcException("invalid_client", description, 401);
  }

  public static OidcException invalidToken(String description) {
    return new OidcException("invalid_token", description, 401);
  }
}
