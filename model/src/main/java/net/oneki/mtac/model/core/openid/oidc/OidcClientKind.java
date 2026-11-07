package net.oneki.mtac.model.core.openid.oidc;

/** Kind of application chosen when a client is created; it presets the other fields. */
public enum OidcClientKind {
  WEB, // server-side application: confidential, HTTPS redirect
  SPA, // browser application: public, PKCE, allowed origins
  NATIVE, // native or command line application: public, PKCE, loopback redirect
  AI_CONNECTOR // chatbot or agent connector (claude.ai, ChatGPT...): public, PKCE, API access
}
