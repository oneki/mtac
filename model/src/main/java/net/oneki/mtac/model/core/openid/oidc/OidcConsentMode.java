package net.oneki.mtac.model.core.openid.oidc;

/** When the provider asks the user to approve a client before issuing a code. */
public enum OidcConsentMode {
  TRUSTED, // first-party client: never asked
  REMEMBERED, // asked once, then again only for scopes not approved yet
  ALWAYS // asked on every authorization
}
