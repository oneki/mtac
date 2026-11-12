package net.oneki.mtac.core.service.openid.oidc;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.repository.OidcConsentRepository;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.config.MtacProperties.MtacOidcClient;
import net.oneki.mtac.model.core.openid.oidc.OidcAuthorizationRequest;
import net.oneki.mtac.model.core.openid.oidc.OidcClientConsent;
import net.oneki.mtac.model.core.openid.oidc.OidcConsent;
import net.oneki.mtac.model.core.openid.oidc.OidcConsentMode;
import net.oneki.mtac.model.core.openid.oidc.OidcSession;
import net.oneki.mtac.model.core.openid.oidc.OidcUserConsent;
import net.oneki.mtac.model.resource.Resource;

/**
 * Consents of the OpenID provider: whether a client must be approved by the
 * user before a code is issued, what the user approved, and the listing and
 * revocation of the approvals.
 *
 * <p>
 * A TRUSTED client (first-party, the yml clients by default) is never asked.
 * A REMEMBERED client is asked once, then again only when it requests a scope
 * not approved yet or sends {@code prompt=consent}. An ALWAYS client is asked
 * on every authorization. A client without consent mode is treated as
 * REMEMBERED: asking too often is safer than not asking.
 *
 * <p>
 * Step-up: the SSO session lasts long (days), so a browser left signed in must
 * not be enough to grant a new access. When the consent page is about to be
 * shown and the session's authentication is older than
 * {@code mtac.openid.server.consent-step-up-sec}, the user proves himself again
 * first (the TOTP when he has MFA, the password otherwise). Reusing a consent
 * that covers the request stays silent. A value of 0 or less disables the
 * step-up.
 */
@Service
@Slf4j
public class OidcConsentService {
  public static final String PROMPT_CONSENT = "consent";

  /** Prefix of the pseudo-scope that records the resource (audience key) a consent was given for. */
  public static final String RESOURCE_SCOPE_PREFIX = "resource:";

  /** Text shown for the well-known scopes; mtac.openid.server.scope-labels overrides them. */
  public static final Map<String, String> DEFAULT_SCOPE_LABELS;
  static {
    var labels = new LinkedHashMap<String, String>();
    labels.put("openid", "Know who you are (your account identifier)");
    labels.put("profile", "See your name");
    labels.put("email", "See your email address");
    labels.put("offline_access", "Stay connected when you are not using it");
    labels.put("fuzz:read", "Read your sites, assets, measurements and alerts");
    labels.put("fuzz:config:write", "Change the configuration of your sites and assets");
    labels.put("fuzz:control", "Send commands to your assets (start, stop, change the power)");
    labels.put(RESOURCE_SCOPE_PREFIX + "mcp", "Use the Fuzz MCP server (tools for AI assistants)");
    labels.put(RESOURCE_SCOPE_PREFIX + "api", "Use the Fuzz public API");
    DEFAULT_SCOPE_LABELS = Map.copyOf(labels);
  }
  private static final String TENANTS_SCOPE_LABEL = "See your companies and your roles in them";

  private final OidcConsentRepository consentRepository;
  private final OidcClientRegistry clientRegistry;
  private final MtacProperties mtacProperties;

  public OidcConsentService(OidcConsentRepository consentRepository, OidcClientRegistry clientRegistry,
      MtacProperties mtacProperties) {
    this.consentRepository = consentRepository;
    this.clientRegistry = clientRegistry;
    this.mtacProperties = mtacProperties;
  }

  // ------------------------------------------------------------ decision

  /** True when the client is never asked for a consent. */
  public static boolean isTrusted(MtacOidcClient client) {
    return client.getConsentMode() == OidcConsentMode.TRUSTED;
  }

  /**
   * True when the consent page must be shown before a code is issued for this
   * request to the signed-in identity.
   */
  public boolean isConsentRequired(MtacOidcClient client, OidcAuthorizationRequest request, Integer identityId) {
    if (isTrusted(client)) {
      return false;
    }
    if (client.getConsentMode() == OidcConsentMode.ALWAYS) {
      return true;
    }
    if (request.getPrompt() != null && request.getPrompt().contains(PROMPT_CONSENT)) {
      return true;
    }
    var consent = consentRepository.find(identityId, request.getClientId());
    if (consent == null || consent.getScopes() == null) {
      return true;
    }
    return !consent.getScopes().containsAll(consentScopes(request));
  }

  /**
   * What a consent covers: the scopes, plus the resource asked through the
   * resource parameter as a pseudo-scope, so that a consent given for one
   * resource never silently covers another.
   */
  public static Set<String> consentScopes(OidcAuthorizationRequest request) {
    var scopes = new LinkedHashSet<String>();
    if (request.getScopes() != null) {
      scopes.addAll(request.getScopes());
    }
    if (request.getResource() != null) {
      scopes.add(RESOURCE_SCOPE_PREFIX + request.getResource());
    }
    return scopes;
  }

  /**
   * True when the user of the session must prove himself again before the
   * consent page is shown: his last authentication is older than
   * {@code consent-step-up-sec}. Only asked when a consent page is due.
   */
  public boolean isStepUpRequired(OidcSession session) {
    var maxAge = mtacProperties.getOpenId().getServer().getConsentStepUpSec();
    if (maxAge <= 0) {
      return false;
    }
    return session.getAuthTime() == null || session.getAuthTime().plusSeconds(maxAge).isBefore(Instant.now());
  }

  /** The user approved these scopes: they are added to the stored consent. */
  public void recordApproval(Integer identityId, String clientId, Collection<String> scopes) {
    consentRepository.upsert(identityId, clientId, scopes, Instant.now());
  }

  /** A code was issued with the stored consent. */
  public void recordUse(Integer identityId, String clientId) {
    consentRepository.touch(identityId, clientId, Instant.now());
  }

  // ------------------------------------------------------- user consents

  /** The consents of an identity, with what the "Connected applications" page shows. */
  public List<OidcUserConsent> listOfIdentity(Integer identityId) {
    var result = new ArrayList<OidcUserConsent>();
    for (var consent : consentRepository.listByIdentity(identityId)) {
      var client = findClient(consent.getClientId());
      var scopes = new ArrayList<>(consent.getScopes());
      result.add(OidcUserConsent.builder()
          .clientId(consent.getClientId())
          .clientName(client != null ? client.getName() : null)
          .clientHost(clientHost(consent.getClientId(), client, null))
          .loopback(client != null && isLoopbackOnly(client))
          .scopes(scopes)
          .scopeLabels(scopeLabels(scopes))
          .createdAt(consent.getCreatedAt())
          .updatedAt(consent.getUpdatedAt())
          .lastUsedAt(consent.getLastUsedAt())
          .build());
    }
    return result;
  }

  /**
   * Revokes what the identity gave to the client: the consent, the refresh
   * tokens and the codes not exchanged yet. The access tokens already issued
   * stay valid until they expire (a few minutes).
   */
  public void revoke(Integer identityId, String clientId) {
    var deleted = consentRepository.delete(identityId, clientId);
    var revoked = consentRepository.revokeGrants(identityId, clientId, Instant.now());
    log.info("OIDC consent of identity {} for client {} revoked (consent deleted: {}, {} refresh token(s) revoked)",
        identityId, clientId, deleted, revoked);
  }

  // ----------------------------------------------------- client consents

  /** The consents given to a client. The caller checks that the user is a super admin. */
  public List<OidcClientConsent> listOfClient(String clientId) {
    var result = new ArrayList<OidcClientConsent>();
    for (var consent : consentRepository.listByClient(clientId)) {
      result.add(toClientConsent(consent));
    }
    return result;
  }

  /** Deletes the consents of every user for a client (client deleted, or its redirect URIs changed). */
  public int deleteByClient(String clientId) {
    return consentRepository.deleteByClient(clientId);
  }

  private static OidcClientConsent toClientConsent(OidcConsent consent) {
    String uid;
    try {
      uid = Resource.toUid(consent.getIdentityId());
    } catch (RuntimeException e) {
      uid = null;
    }
    return OidcClientConsent.builder()
        .identityUid(uid)
        .identityLabel(consent.getIdentityLabel())
        .scopes(new ArrayList<>(consent.getScopes()))
        .createdAt(consent.getCreatedAt())
        .updatedAt(consent.getUpdatedAt())
        .lastUsedAt(consent.getLastUsedAt())
        .build();
  }

  // -------------------------------------------------------------- display

  public String scopeLabel(String scope) {
    var server = mtacProperties.getOpenId().getServer();
    var configured = server.getScopeLabels() != null ? server.getScopeLabels().get(scope) : null;
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    var label = DEFAULT_SCOPE_LABELS.get(scope);
    if (label != null) {
      return label;
    }
    if (scope != null && scope.equals(server.getTenantsScope())) {
      return TENANTS_SCOPE_LABEL;
    }
    return scope;
  }

  public Map<String, String> scopeLabels(Collection<String> scopes) {
    var result = new LinkedHashMap<String, String>();
    for (var scope : scopes) {
      result.put(scope, scopeLabel(scope));
    }
    return result;
  }

  /**
   * Host that identifies the client to the user: the host of the client_id
   * when it is an HTTPS URL (Client ID Metadata Document), else the host of the
   * redirect URI of the request, else of the first registered redirect URI that
   * is not on this computer (or the first one).
   */
  public static String clientHost(String clientId, MtacOidcClient client, String redirectUri) {
    if (clientId != null && clientId.startsWith("https://")) {
      var host = host(clientId);
      if (host != null) {
        return host;
      }
    }
    if (redirectUri != null) {
      return host(redirectUri);
    }
    if (client == null || client.getRedirectUris() == null || client.getRedirectUris().isEmpty()) {
      return null;
    }
    for (var uri : client.getRedirectUris()) {
      if (OidcClientRegistry.parseLoopback(uri) == null && host(uri) != null) {
        return host(uri);
      }
    }
    return host(client.getRedirectUris().get(0));
  }

  /**
   * Host (with its port when there is one) of an URI, or null. A private-use
   * URI (RFC 8252 section 7.1) opens an application: its scheme, which names
   * that application, is kept ({@code cursor://anysphere.cursor-mcp}).
   */
  public static String host(String value) {
    if (value == null) {
      return null;
    }
    try {
      var uri = new URI(value);
      if (isPrivateUse(uri)) {
        return uri.getHost() != null ? uri.getScheme() + "://" + uri.getHost() : uri.getScheme() + ":";
      }
      if (uri.getHost() == null) {
        return null;
      }
      return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * True when every redirect URI of the client points to this computer (a
   * loopback or a private-use URI): a native application.
   */
  public static boolean isLoopbackOnly(MtacOidcClient client) {
    var uris = client.getRedirectUris();
    if (uris == null || uris.isEmpty()) {
      return false;
    }
    for (var value : uris) {
      if (!isOnThisComputer(value)) {
        return false;
      }
    }
    return true;
  }

  /** True when the redirect URI points to this computer: a loopback or a private-use URI. */
  public static boolean isOnThisComputer(String value) {
    URI uri;
    try {
      uri = new URI(value);
    } catch (Exception e) {
      return false;
    }
    if (isPrivateUse(uri)) {
      return true;
    }
    var host = uri.getHost(); // [::1] keeps its brackets
    return "http".equalsIgnoreCase(uri.getScheme()) && host != null && OidcClientRegistry.LOOPBACK_HOSTS.contains(host);
  }

  /** An absolute URI whose scheme is neither http nor https (the validator only lets allowed ones in). */
  private static boolean isPrivateUse(URI uri) {
    var scheme = uri.getScheme();
    return scheme != null && !"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme);
  }

  private MtacOidcClient findClient(String clientId) {
    try {
      // display only: a metadata document client is shown from the cache, never fetched here
      return clientRegistry.findCached(clientId);
    } catch (RuntimeException e) {
      log.warn("OIDC client {} of a consent cannot be resolved: {}", clientId, e.getMessage());
      return null;
    }
  }

  /** The scopes of a request, normalised the way they are stored. */
  public static Set<String> normalized(Collection<String> scopes) {
    return OidcConsentRepository.splitScopes(OidcConsentRepository.joinScopes(scopes));
  }
}
