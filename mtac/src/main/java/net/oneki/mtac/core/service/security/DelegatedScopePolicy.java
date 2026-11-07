package net.oneki.mtac.core.service.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Scope ceiling of the delegated access tokens: the tokens an OAuth client
 * obtained on behalf of a user through the authorization code flow with a
 * resource indicator (RFC 8707). Such a token carries a {@code client_id}
 * claim (RFC 9068), which {@code JwtAuthenticationManager} copies into the
 * principal attributes with the granted {@code scope}.
 *
 * <p>
 * Effective rights = the user's RBAC ∩ the scopes. This class only removes
 * rights: it never grants anything the roles of the user do not give.
 *
 * <p>
 * Mapping of the scopes to permission actions ({@code "<schema>|<action>"} or
 * a plain action checked on the schema of the resource):
 * <ul>
 * <li>{@value #SCOPE_READ}: the read-only actions of {@link #READ_ACTIONS}
 * (read, list, get, energyUnitsReport, bulkExportData);</li>
 * <li>{@value #SCOPE_CONFIG_WRITE}: the actions of {@link #WRITE_ACTIONS}
 * (create, update, upsert, delete);</li>
 * <li>{@value #SCOPE_CONTROL}: every other action, i.e. the custom actions of
 * the application (rebootController, reset, unlockConnector,
 * remoteStartTransaction, triggerMessage, changeConfiguration, firmware,
 * diagnostics, modbusTester...);</li>
 * <li>never, whatever the scopes: any action on an {@code iam.*} schema
 * (users, groups, applications, roles, OIDC clients, idTags), the actions of
 * {@link #FORBIDDEN_ACTIONS} (impersonate, group membership, password reset,
 * web SSH to an edge device) and the super admin power
 * ({@link PermissionService#isSuperAdmin()} is false).</li>
 * </ul>
 * A delegated token without any of these scopes gets no permission at all
 * (fail closed). Tokens without a {@code client_id} claim (Console, service
 * accounts) are not affected.
 *
 * <p>
 * The Fuzz api adds a second barrier on the HTTP methods
 * ({@code DelegatedScopeFilter}), because list and get requests are filtered
 * by SQL and never reach {@link PermissionService}.
 */
public final class DelegatedScopePolicy {
  /** Principal attribute of the client the token was issued to (RFC 9068 claim). */
  public static final String ATTR_CLIENT_ID = "client_id";
  /** Principal attribute of the granted scopes, space separated (RFC 9068 claim). */
  public static final String ATTR_SCOPE = "scope";

  public static final String SCOPE_READ = "fuzz:read";
  public static final String SCOPE_CONFIG_WRITE = "fuzz:config:write";
  public static final String SCOPE_CONTROL = "fuzz:control";
  public static final List<String> API_SCOPES = List.of(SCOPE_READ, SCOPE_CONFIG_WRITE, SCOPE_CONTROL);

  public static final Set<String> READ_ACTIONS = Set.of("read", "list", "get", "energyUnitsReport", "bulkExportData");
  public static final Set<String> WRITE_ACTIONS = Set.of("create", "update", "upsert", "delete");
  public static final Set<String> FORBIDDEN_ACTIONS = Set.of("impersonate", "addIdentityToGroup",
      "removeIdentityFromGroup", "addUserToGroup", "addAdminUser", "reset_password", "webssh");
  /** Schemas a delegated token never acts on (identities, roles, OIDC clients...). */
  public static final String FORBIDDEN_SCHEMA_PREFIX = "iam.";

  private static final DelegatedScopePolicy NOT_DELEGATED = new DelegatedScopePolicy(null, Set.of());

  private final String clientId;
  private final Set<String> scopes;

  private DelegatedScopePolicy(String clientId, Set<String> scopes) {
    this.clientId = clientId;
    this.scopes = scopes;
  }

  /** Policy of the authenticated principal of the current thread. */
  public static DelegatedScopePolicy current() {
    var context = SecurityContextHolder.getContext();
    var authentication = context != null ? context.getAuthentication() : null;
    if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User principal)) {
      return NOT_DELEGATED;
    }
    return of(principal.getAttributes());
  }

  /** Policy of principal attributes (see {@link #ATTR_CLIENT_ID}, {@link #ATTR_SCOPE}). */
  public static DelegatedScopePolicy of(Map<String, Object> attributes) {
    if (attributes == null) {
      return NOT_DELEGATED;
    }
    var clientId = attributes.get(ATTR_CLIENT_ID);
    if (clientId == null) {
      return NOT_DELEGATED;
    }
    return new DelegatedScopePolicy(clientId.toString(), parseScopes(attributes.get(ATTR_SCOPE)));
  }

  /** True for a token issued to an OAuth client on behalf of the user. */
  public boolean isDelegated() {
    return clientId != null;
  }

  /** The client the token was issued to, or null when not delegated. */
  public String getClientId() {
    return clientId;
  }

  public Set<String> getScopes() {
    return scopes;
  }

  public boolean hasScope(String scope) {
    return scopes.contains(scope);
  }

  /** True when the token may only read: none of the write or control scopes. */
  public boolean isReadOnly() {
    return !hasScope(SCOPE_CONFIG_WRITE) && !hasScope(SCOPE_CONTROL);
  }

  /** A delegated token never has the super admin power. */
  public boolean allowsSuperAdmin() {
    return !isDelegated();
  }

  /**
   * True when the scopes allow the permission. Always true for a token that is
   * not delegated: the RBAC alone decides.
   *
   * @param resourceSchema schema of the resource the permission is checked on
   *                       (may be null)
   * @param permission     a plain action ({@code update}) or a
   *                       {@code "<schema>|<action>"} path
   */
  public boolean allows(String resourceSchema, String permission) {
    if (!isDelegated()) {
      return true;
    }
    if (permission == null || permission.isBlank()) {
      return false;
    }
    var action = permission;
    String targetSchema = null;
    var separator = permission.indexOf('|');
    if (separator >= 0) {
      targetSchema = permission.substring(0, separator);
      action = permission.substring(separator + 1);
    }
    if (isForbiddenSchema(resourceSchema) || isForbiddenSchema(targetSchema)) {
      return false;
    }
    if (action.isEmpty() || action.contains("*") || FORBIDDEN_ACTIONS.contains(action)) {
      return false;
    }
    if (READ_ACTIONS.contains(action)) {
      return hasScope(SCOPE_READ);
    }
    if (WRITE_ACTIONS.contains(action)) {
      return hasScope(SCOPE_CONFIG_WRITE);
    }
    return hasScope(SCOPE_CONTROL);
  }

  /** Same as {@link #allows(String, String)} for the creation of a resource of this schema. */
  public boolean allowsCreate(String schemaLabel) {
    return allows(schemaLabel, "create");
  }

  private static boolean isForbiddenSchema(String schema) {
    // a wildcard schema could cover iam.*: refused as well
    return schema != null && (schema.startsWith(FORBIDDEN_SCHEMA_PREFIX) || schema.contains("*"));
  }

  private static Set<String> parseScopes(Object value) {
    if (value == null) {
      return Set.of();
    }
    var result = new LinkedHashSet<String>();
    if (value instanceof java.util.Collection<?> collection) {
      for (var item : collection) {
        if (item != null) {
          result.add(item.toString());
        }
      }
    } else {
      var text = value.toString().trim();
      if (!text.isEmpty()) {
        result.addAll(Arrays.asList(text.split("\\s+")));
      }
    }
    return Collections.unmodifiableSet(result);
  }
}
