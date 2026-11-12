package net.oneki.mtac.resource.iam.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.service.openid.oidc.OidcClientRegistry;
import net.oneki.mtac.framework.service.TokenAudiences;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.openid.oidc.OidcAccessMode;
import net.oneki.mtac.model.core.openid.oidc.OidcClientKind;
import net.oneki.mtac.model.core.openid.oidc.OidcClientSource;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import net.oneki.mtac.model.core.openid.oidc.TokenAudience;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.iam.oidc.OidcClient;

/**
 * Rules every iam.oidc.client must follow, whoever creates it (super admin,
 * Dynamic Client Registration). {@link #normalize(OidcClient)} trims and
 * deduplicates the lists, {@link #validate(OidcClient)} throws a
 * {@link BusinessException} whose code names the broken rule.
 */
@Component
@RequiredArgsConstructor
public class OidcClientValidator {
  public static final String SCOPE_OPENID = "openid";
  public static final String SCOPE_OFFLINE_ACCESS = "offline_access";
  /** Scopes of the Fuzz API (step D2). */
  public static final List<String> API_SCOPES = List.of("fuzz:read", "fuzz:config:write", "fuzz:control");
  /** Audience keys a client may ask a token for (never the Console). */
  public static final Set<String> ALLOWED_RESOURCES = Set.of(TokenAudience.API, TokenAudience.MCP);

  public static final int MAX_NAME_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 1000;
  public static final int MAX_URIS = 20;
  public static final int MAX_URI_LENGTH = 2048;
  public static final int MAX_SECRETS = 2;
  public static final int MAX_SESSION_DAYS = 365;
  /** Schemes no configuration can turn into a private-use redirect scheme: web, script, local file or browser ones. */
  public static final Set<String> RESERVED_SCHEMES = Set.of("http", "https", "javascript", "vbscript", "data", "file",
      "blob", "filesystem", "about", "view-source", "chrome", "intent", "ftp", "ws", "wss", "mailto", "tel", "sms");

  private final MtacProperties mtacProperties;
  private final TokenAudiences tokenAudiences;

  /** Scopes a client may list: standard OIDC ones, the tenants scope and the Fuzz API scopes. */
  public Set<String> knownScopes() {
    var result = new LinkedHashSet<String>();
    result.add(SCOPE_OPENID);
    result.add("profile");
    result.add("email");
    var tenantsScope = mtacProperties.getOpenId().getServer().getTenantsScope();
    if (tenantsScope != null && !tenantsScope.isBlank()) {
      result.add(tenantsScope);
    }
    result.add(SCOPE_OFFLINE_ACCESS);
    result.addAll(API_SCOPES);
    return result;
  }

  /** The private-use redirect schemes of the configuration ({@code dynamic.allowed-redirect-schemes}). */
  public Set<String> privateUseSchemes() {
    return privateUseSchemes(mtacProperties.getOpenId().getServer().getDynamic().getAllowedRedirectSchemes());
  }

  /** Configured private-use schemes, lower case, without blanks, invalid names and {@link #RESERVED_SCHEMES}. */
  public static Set<String> privateUseSchemes(List<String> configured) {
    var result = new LinkedHashSet<String>();
    if (configured != null) {
      for (var scheme : configured) {
        var value = scheme != null ? scheme.trim().toLowerCase(Locale.ROOT) : "";
        if (value.matches("[a-z][a-z0-9+.-]*") && !RESERVED_SCHEMES.contains(value)) {
          result.add(value);
        }
      }
    }
    return result;
  }

  /** True when the URI uses one of the private-use schemes (compared case-insensitively). */
  public static boolean isPrivateUse(String value, Set<String> privateUseSchemes) {
    if (value == null || privateUseSchemes == null || privateUseSchemes.isEmpty()) {
      return false;
    }
    var colon = value.indexOf(':');
    return colon > 0 && privateUseSchemes.contains(value.substring(0, colon).toLowerCase(Locale.ROOT));
  }

  /**
   * Why a private-use redirect URI cannot be registered, or null when it can:
   * hierarchical ({@code scheme:/path} or {@code scheme://host/path}), no
   * fragment, user info, wildcard, white space or control character. The
   * scheme itself is checked by {@link #isPrivateUse(String, Set)}.
   */
  public static String checkPrivateUseRedirectUri(String value) {
    if (value == null || value.isEmpty() || value.length() > MAX_URI_LENGTH || value.indexOf('*') >= 0) {
      return "not a valid absolute URI";
    }
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      if (c <= 0x20 || c >= 0x7f) {
        return "not a valid absolute URI";
      }
    }
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return "not a valid absolute URI";
    }
    if (!uri.isAbsolute() || uri.isOpaque()) {
      return "not a valid absolute URI";
    }
    if (uri.getRawFragment() != null || value.indexOf('#') >= 0) {
      return "a fragment is not allowed";
    }
    if (uri.getRawUserInfo() != null || value.indexOf('@') >= 0) {
      return "user information is not allowed";
    }
    return null;
  }

  /** A client_id must never equal an audience, nor shadow or be shadowed by a yml client. */
  public void validateClientId(String clientId, OidcClientRegistry registry) {
    if (clientId == null || !clientId.matches("[a-z0-9][a-z0-9-]{2,63}")) {
      throw new BusinessException("OIDC_CLIENT_INVALID_ID", "Invalid client_id");
    }
    if (tokenAudiences.isReserved(clientId)) {
      throw new BusinessException("OIDC_CLIENT_RESERVED_ID", "The client_id " + clientId + " is reserved");
    }
    if (registry != null && registry.isYmlClient(clientId)) {
      throw new BusinessException("OIDC_CLIENT_RESERVED_ID",
          "The client_id " + clientId + " is already declared in the configuration");
    }
  }

  /** Trims, removes blanks and duplicates, applies the defaults of the optional fields. */
  public void normalize(OidcClient client) {
    client.setName(trim(client.getName()));
    client.setDescription(trim(client.getDescription()));
    client.setRedirectUris(clean(client.getRedirectUris()));
    client.setPostLogoutRedirectUris(clean(client.getPostLogoutRedirectUris()));
    client.setAllowedOrigins(clean(client.getAllowedOrigins()));
    client.setScopes(clean(client.getScopes()));
    client.setResources(clean(client.getResources()));
    if (client.getSecrets() == null) {
      client.setSecrets(new ArrayList<>());
    }
    if (client.getConsentMode() == null) {
      client.setConsentMode(OidcConsentMode.REMEMBERED);
    }
    if (client.getAccessMode() == null) {
      client.setAccessMode(OidcAccessMode.IDENTITY);
    }
    if (client.getSource() == null) {
      client.setSource(OidcClientSource.CONSOLE);
    }
  }

  public void validate(OidcClient client) {
    normalize(client);

    if (client.getName() == null || client.getName().isEmpty()) {
      throw new BusinessException("OIDC_CLIENT_NAME_REQUIRED", "The name is required");
    }
    if (client.getName().length() > MAX_NAME_LENGTH) {
      throw new BusinessException("OIDC_CLIENT_NAME_TOO_LONG",
          "The name cannot be longer than " + MAX_NAME_LENGTH + " characters");
    }
    if (client.getDescription() != null && client.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
      throw new BusinessException("OIDC_CLIENT_DESCRIPTION_TOO_LONG",
          "The description cannot be longer than " + MAX_DESCRIPTION_LENGTH + " characters");
    }
    if (client.getKind() == null) {
      throw new BusinessException("OIDC_CLIENT_KIND_REQUIRED", "The kind of client is required");
    }

    validateConfidentiality(client);

    var allowLoopbackHttp = client.getKind() == OidcClientKind.NATIVE || client.isLoopback();
    if (client.getRedirectUris().isEmpty()) {
      throw new BusinessException("OIDC_CLIENT_REDIRECT_URI_REQUIRED", "At least one redirect URI is required");
    }
    validateUris(client.getRedirectUris(), allowLoopbackHttp, "OIDC_CLIENT_INVALID_REDIRECT_URI");
    validateUris(client.getPostLogoutRedirectUris(), allowLoopbackHttp,
        "OIDC_CLIENT_INVALID_POST_LOGOUT_REDIRECT_URI");
    validateOrigins(client.getAllowedOrigins());

    validateScopes(client.getScopes());
    validateResources(client);

    if (client.getConsentMode() == OidcConsentMode.TRUSTED && client.getSource() != OidcClientSource.CONSOLE) {
      throw new BusinessException("OIDC_CLIENT_TRUSTED_NOT_ALLOWED",
          "Only a client created by a super admin can skip the consent");
    }
    if (client.getSessionMaxDays() != null
        && (client.getSessionMaxDays() < 1 || client.getSessionMaxDays() > MAX_SESSION_DAYS)) {
      throw new BusinessException("OIDC_CLIENT_INVALID_SESSION_MAX_DAYS",
          "The maximum session length must be between 1 and " + MAX_SESSION_DAYS + " days");
    }
  }

  private void validateConfidentiality(OidcClient client) {
    if (client.isConfidential() && client.getKind() != OidcClientKind.WEB) {
      throw new BusinessException("OIDC_CLIENT_CONFIDENTIAL_NOT_ALLOWED",
          "A " + client.getKind() + " client cannot keep a secret: it must be public");
    }
    var secrets = client.getSecrets();
    if (client.isConfidential()) {
      if (secrets.isEmpty()) {
        throw new BusinessException("OIDC_CLIENT_SECRET_REQUIRED", "A confidential client needs a secret");
      }
      if (secrets.size() > MAX_SECRETS) {
        throw new BusinessException("OIDC_CLIENT_TOO_MANY_SECRETS",
            "A client cannot have more than " + MAX_SECRETS + " secrets");
      }
      for (var secret : secrets) {
        if (secret == null || secret.getId() == null || secret.getHash() == null || secret.getHash().isBlank()) {
          throw new BusinessException("OIDC_CLIENT_INVALID_SECRET", "Invalid secret");
        }
      }
    } else if (!secrets.isEmpty()) {
      throw new BusinessException("OIDC_CLIENT_SECRETS_NOT_ALLOWED", "A public client has no secret");
    }
  }

  private void validateUris(List<String> uris, boolean allowLoopbackHttp, String code) {
    if (uris.size() > MAX_URIS) {
      throw new BusinessException(code, "No more than " + MAX_URIS + " URIs are allowed");
    }
    var privateUseSchemes = privateUseSchemes();
    for (var uri : uris) {
      var reason = isPrivateUse(uri, privateUseSchemes) ? checkPrivateUseRedirectUri(uri)
          : checkRedirectUri(uri, allowLoopbackHttp);
      if (reason != null) {
        throw new BusinessException(code, "Invalid URI " + uri + ": " + reason).param("uri", uri);
      }
    }
  }

  /**
   * Why a redirect URI cannot be registered, or null when it can: absolute
   * {@code https} URI without fragment, userinfo or wildcard; {@code http} only
   * for a loopback host and when the client allows it.
   */
  public static String checkRedirectUri(String value, boolean allowLoopbackHttp) {
    var uri = parseStrict(value);
    if (uri == null) {
      return "not a valid absolute URI";
    }
    if (uri.getRawFragment() != null || value.indexOf('#') >= 0) {
      return "a fragment is not allowed";
    }
    if (uri.getRawUserInfo() != null) {
      return "user information is not allowed";
    }
    if ("https".equals(uri.getScheme())) {
      return null;
    }
    if ("http".equals(uri.getScheme())) {
      if (OidcClientRegistry.parseLoopback(value) == null) {
        return "http is only allowed for localhost, 127.0.0.1 or [::1]";
      }
      if (!allowLoopbackHttp) {
        return "http loopback URIs need a native client or the loopback option";
      }
      return null;
    }
    return "the scheme must be https (or a private-use scheme of the configuration)";
  }

  private void validateOrigins(List<String> origins) {
    if (origins.size() > MAX_URIS) {
      throw new BusinessException("OIDC_CLIENT_INVALID_ORIGIN", "No more than " + MAX_URIS + " origins are allowed");
    }
    for (var origin : origins) {
      var reason = checkOrigin(origin);
      if (reason != null) {
        throw new BusinessException("OIDC_CLIENT_INVALID_ORIGIN", "Invalid origin " + origin + ": " + reason)
            .param("origin", origin);
      }
    }
  }

  /**
   * Why an origin cannot be listed, or null when it can: {@code scheme://host[:port]}
   * exactly as a browser sends it, https or http loopback.
   */
  public static String checkOrigin(String value) {
    var uri = parseStrict(value);
    if (uri == null) {
      return "not a valid origin";
    }
    if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
      return "an origin is scheme://host[:port], without path";
    }
    if ("https".equals(uri.getScheme())) {
      return null;
    }
    if ("http".equals(uri.getScheme()) && OidcClientRegistry.parseLoopback(value) != null) {
      return null;
    }
    return "the scheme must be https (http only for localhost, 127.0.0.1 or [::1])";
  }

  private void validateScopes(List<String> scopes) {
    if (!scopes.contains(SCOPE_OPENID)) {
      throw new BusinessException("OIDC_CLIENT_OPENID_SCOPE_REQUIRED", "The scope openid is required");
    }
    var known = knownScopes();
    for (var scope : scopes) {
      if (!known.contains(scope)) {
        throw new BusinessException("OIDC_CLIENT_UNKNOWN_SCOPE", "Unknown scope " + scope).param("scope", scope);
      }
    }
  }

  private void validateResources(OidcClient client) {
    var resources = client.getResources();
    for (var resource : resources) {
      if (!ALLOWED_RESOURCES.contains(resource)) {
        throw new BusinessException("OIDC_CLIENT_INVALID_RESOURCE", "Unknown resource " + resource)
            .param("resource", resource);
      }
    }
    if (client.getAccessMode() == OidcAccessMode.API) {
      if (resources.isEmpty()) {
        throw new BusinessException("OIDC_CLIENT_RESOURCE_REQUIRED",
            "A client with API access needs at least one resource");
      }
    } else if (!resources.isEmpty()) {
      throw new BusinessException("OIDC_CLIENT_RESOURCES_REQUIRE_API_ACCESS",
          "Resources are only allowed with the API access mode");
    }
  }

  /**
   * Absolute, hierarchical URI with a server-based authority, no wildcard, no
   * whitespace; null otherwise.
   */
  private static URI parseStrict(String value) {
    if (value == null || value.isEmpty() || value.length() > MAX_URI_LENGTH || value.indexOf('*') >= 0) {
      return null;
    }
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      if (c <= 0x20 || c >= 0x7f) {
        return null;
      }
    }
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return null;
    }
    if (!uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null || uri.getHost().isEmpty()) {
      return null;
    }
    var port = uri.getPort();
    if (port == 0 || port > 65535) {
      return null;
    }
    return uri;
  }

  private static String trim(String value) {
    if (value == null) {
      return null;
    }
    var result = value.trim();
    return result.isEmpty() ? null : result;
  }

  private static List<String> clean(List<String> values) {
    var result = new LinkedHashSet<String>();
    if (values != null) {
      for (var value : values) {
        var trimmed = trim(value);
        if (trimmed != null) {
          result.add(trimmed);
        }
      }
    }
    return new ArrayList<>(result);
  }
}
