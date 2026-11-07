package net.oneki.mtac.model.core.openid.oidc;

/** What the access tokens issued to a client can be used for. */
public enum OidcAccessMode {
  IDENTITY, // userinfo only: the token is refused by every API
  API // the client may ask a resource (RFC 8707) among its allowed resources
}
