package net.oneki.mtac.model.core.openid.oidc;

/** Where the provider found the description of a client. */
public enum OidcClientSource {
  YML, // mtac.openid.server.clients
  CONSOLE, // iam.oidc.client resource created by a super admin
  DCR, // iam.oidc.client resource created by Dynamic Client Registration
  CIMD // Client ID Metadata Document fetched from the client_id URL
}
