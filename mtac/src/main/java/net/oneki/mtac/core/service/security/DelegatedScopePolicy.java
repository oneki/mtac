package net.oneki.mtac.core.service.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import net.oneki.mtac.model.core.openid.oidc.TokenAudience;

/**
 * Scope ceiling of the delegated access tokens: the tokens an OAuth client
 * obtained on behalf of a user through the authorization code flow with a
 * resource indicator (RFC 8707). Such a token carries a {@code client_id}
 * claim (RFC 9068), which {@code JwtAuthenticationManager} copies into the
 * principal attributes with the granted {@code scope} and the key of its
 * audience.
 *
 * <p>
 * Two kinds of delegated tokens ({@link Kind}):
 * <ul>
 * <li>{@link Kind#SCOPED}: third-party clients (connectors, AI agents, DCR,
 * CIMD, clients created by a super admin), audience api or mcp. Effective
 * rights = the user's RBAC ∩ the scopes, see below;</li>
 * <li>{@link Kind#FIRST_PARTY_MOBILE}: the first-party mobile applications,
 * audience mobile, which only a yml client declaring it in
 * {@code first-party-audiences} can obtain. Effective rights = the user's full
 * RBAC (no scope reduction), minus {@link #MOBILE_FORBIDDEN_ACTIONS}
 * (impersonate, webssh, addAdminUser), minus any {@code iam.*} schema and the
 * IAM actions of {@link #IAM_ACTIONS} outside an explicit
 * {@link #allowIam(String, Supplier)} elevation, and without the super admin
 * power outside {@link #withSuperAdminPowers(String, Supplier)}.</li>
 * </ul>
 * This class only removes rights: it never grants anything the roles of the
 * user do not give.
 *
 * <p>
 * Mapping of the scopes of a {@link Kind#SCOPED} token to permission actions
 * ({@code "<schema>|<action>"} or a plain action checked on the schema of the
 * resource):
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
 * A scoped token without any of these scopes gets no permission at all (fail
 * closed). Tokens without a {@code client_id} claim (Console, service
 * accounts) are not affected.
 *
 * <p>
 * The Fuzz api adds a second barrier on the HTTP methods and paths
 * ({@code DelegatedScopeFilter}), because list and get requests are filtered
 * by SQL and never reach {@link PermissionService}.
 */
public final class DelegatedScopePolicy {
  private static final Logger log = LoggerFactory.getLogger(DelegatedScopePolicy.class);

  /** Principal attribute of the client the token was issued to (RFC 9068 claim). */
  public static final String ATTR_CLIENT_ID = "client_id";
  /** Principal attribute of the granted scopes, space separated (RFC 9068 claim). */
  public static final String ATTR_SCOPE = "scope";
  /**
   * Principal attribute of the key of the token audience (see
   * {@link TokenAudience}), taken from the signed aud claim of a delegated
   * token by {@code JwtAuthenticationManager}.
   */
  public static final String ATTR_AUDIENCE = "token_audience";
  /**
   * Principal attribute of the authentication methods of the sign-in the token
   * comes from (signed {@code amr} claim, RFC 8176), copied by
   * {@code JwtAuthenticationManager} for a delegated token.
   */
  public static final String ATTR_AMR = "amr";
  /**
   * The {@code amr} value of the second factor of the provider (TOTP code,
   * {@code OidcSessionService.AMR_OTP}): a sign-in with MFA.
   */
  public static final String AMR_OTP = "otp";

  public static final String SCOPE_READ = "fuzz:read";
  public static final String SCOPE_CONFIG_WRITE = "fuzz:config:write";
  public static final String SCOPE_CONTROL = "fuzz:control";
  public static final List<String> API_SCOPES = List.of(SCOPE_READ, SCOPE_CONFIG_WRITE, SCOPE_CONTROL);

  public static final Set<String> READ_ACTIONS = Set.of("read", "list", "get", "energyUnitsReport", "bulkExportData");
  public static final Set<String> WRITE_ACTIONS = Set.of("create", "update", "upsert", "delete");
  public static final Set<String> FORBIDDEN_ACTIONS = Set.of("impersonate", "addIdentityToGroup",
      "removeIdentityFromGroup", "addUserToGroup", "addAdminUser", "reset_password", "webssh");
  /** Actions a first-party mobile token never performs, even inside {@link #allowIam(String, Supplier)}. */
  public static final Set<String> MOBILE_FORBIDDEN_ACTIONS = Set.of("impersonate", "webssh", "addAdminUser");
  /**
   * Identity and access management actions (group membership, password reset)
   * a first-party mobile token performs only inside
   * {@link #allowIam(String, Supplier)}, like the {@code iam.*} schemas.
   */
  public static final Set<String> IAM_ACTIONS = Set.of("addIdentityToGroup", "removeIdentityFromGroup",
      "addUserToGroup", "reset_password");
  /** Schemas a delegated token never acts on (identities, roles, OIDC clients...). */
  public static final String FORBIDDEN_SCHEMA_PREFIX = "iam.";

  /** Kind of the token of the current principal. */
  public enum Kind {
    /** Not delegated (Console, service accounts, anonymous): the RBAC alone decides. */
    NONE,
    /** Third-party client (api or mcp audience): the RBAC capped by the fuzz:* scopes. */
    SCOPED,
    /** First-party mobile application (mobile audience): the full RBAC with the mobile limits. */
    FIRST_PARTY_MOBILE
  }

  private static final DelegatedScopePolicy NOT_DELEGATED = new DelegatedScopePolicy(Kind.NONE, null, Set.of(), null,
      Set.of());

  // reason of the IAM elevation of the current thread, null outside allowIam
  private static final ThreadLocal<String> IAM_ELEVATION = new ThreadLocal<>();
  // reason of the super admin powers of the current thread, null outside withSuperAdminPowers
  private static final ThreadLocal<String> SUPER_ADMIN_POWERS = new ThreadLocal<>();

  private final Kind kind;
  private final String clientId;
  private final Set<String> scopes;
  private final String subject; // for the logs only
  private final Set<String> amr;

  private DelegatedScopePolicy(Kind kind, String clientId, Set<String> scopes, String subject, Set<String> amr) {
    this.kind = kind;
    this.clientId = clientId;
    this.scopes = scopes;
    this.subject = subject;
    this.amr = amr;
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

  /**
   * Policy of principal attributes (see {@link #ATTR_CLIENT_ID},
   * {@link #ATTR_SCOPE}, {@link #ATTR_AUDIENCE}). A delegated token whose
   * audience is {@link TokenAudience#MOBILE} is {@link Kind#FIRST_PARTY_MOBILE}:
   * the provider issues that audience to the first-party yml clients only.
   */
  public static DelegatedScopePolicy of(Map<String, Object> attributes) {
    if (attributes == null) {
      return NOT_DELEGATED;
    }
    var clientId = attributes.get(ATTR_CLIENT_ID);
    if (clientId == null) {
      return NOT_DELEGATED;
    }
    var kind = TokenAudience.MOBILE.equals(attributes.get(ATTR_AUDIENCE)) ? Kind.FIRST_PARTY_MOBILE : Kind.SCOPED;
    var subject = attributes.get("sub");
    return new DelegatedScopePolicy(kind, clientId.toString(), parseScopes(attributes.get(ATTR_SCOPE)),
        subject != null ? subject.toString() : null, parseScopes(attributes.get(ATTR_AMR)));
  }

  public Kind getKind() {
    return kind;
  }

  /** True for a token issued to an OAuth client on behalf of the user (third-party or first-party mobile). */
  public boolean isDelegated() {
    return kind != Kind.NONE;
  }

  /** True for a token of a first-party mobile application (audience mobile). */
  public boolean isFirstPartyMobile() {
    return kind == Kind.FIRST_PARTY_MOBILE;
  }

  /** True when the token of the current thread is a first-party mobile one. */
  public static boolean isFirstPartyMobileRequest() {
    return current().isFirstPartyMobile();
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

  /** Authentication methods of the sign-in of a delegated token ({@code amr}), empty when not delegated. */
  public Set<String> getAmr() {
    return amr;
  }

  /**
   * True when the delegated token comes from a sign-in with the second factor
   * ({@code amr} contains {@value #AMR_OTP}). Always false for a token that is
   * not delegated: its MFA is not carried this way.
   */
  public boolean isMfaAuthenticated() {
    return isDelegated() && amr.contains(AMR_OTP);
  }

  /**
   * True when the token may only read: none of the write or control scopes.
   * Meaningful for a {@link Kind#SCOPED} token only: a first-party mobile token
   * is never limited by scopes (false).
   */
  public boolean isReadOnly() {
    if (kind == Kind.FIRST_PARTY_MOBILE) {
      return false;
    }
    return !hasScope(SCOPE_CONFIG_WRITE) && !hasScope(SCOPE_CONTROL);
  }

  /**
   * Whether the super admin power applies to this token: always for a token
   * that is not delegated, never for a third-party one, and for a first-party
   * mobile one only inside {@link #withSuperAdminPowers(String, Supplier)} on
   * the current thread (off by default).
   */
  public boolean allowsSuperAdmin() {
    return switch (kind) {
      case NONE -> true;
      case SCOPED -> false;
      case FIRST_PARTY_MOBILE -> SUPER_ADMIN_POWERS.get() != null;
    };
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
    if (kind == Kind.FIRST_PARTY_MOBILE) {
      return allowsFirstPartyMobile(resourceSchema, targetSchema, action);
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

  /** The full RBAC, minus the mobile forbidden actions and, outside allowIam, the IAM schemas and actions. */
  private static boolean allowsFirstPartyMobile(String resourceSchema, String targetSchema, String action) {
    // a wildcard action would cover impersonate or webssh
    if (action.isEmpty() || action.contains("*") || MOBILE_FORBIDDEN_ACTIONS.contains(action)) {
      return false;
    }
    if (isIamElevated()) {
      return true;
    }
    return !isForbiddenSchema(resourceSchema) && !isForbiddenSchema(targetSchema) && !IAM_ACTIONS.contains(action);
  }

  /** Same as {@link #allows(String, String)} for the creation of a resource of this schema. */
  public boolean allowsCreate(String schemaLabel) {
    return allows(schemaLabel, "create");
  }

  // ------------------------------------------------------------ elevations

  /**
   * Runs an identity and access management operation for a first-party mobile
   * token: the {@code iam.*} barrier (schemas and {@link #IAM_ACTIONS}) is
   * lifted for the current thread during the call only, and restored after it,
   * also when it throws. The user's own RBAC is still checked by
   * {@link PermissionService}, and {@link #MOBILE_FORBIDDEN_ACTIONS} stay
   * forbidden. Each use is logged with its reason.
   *
   * <p>
   * Only the app services that need IAM call it (the employees and badges of a
   * company, the installer badges). For any other token it only runs the
   * action: a Console token needs no elevation, a third-party token never gets
   * one.
   *
   * @param reason why the elevation is needed, for the logs (required)
   * @param action the IAM operation, run on the current thread
   */
  public static <T> T allowIam(String reason, Supplier<T> action) {
    Objects.requireNonNull(action, "action");
    requireReason(reason);
    var policy = current();
    if (!policy.isFirstPartyMobile()) {
      return action.get();
    }
    log.info("IAM elevation of a first-party mobile token: {} (client_id={}, sub={})", reason, policy.clientId,
        policy.subject);
    return runWith(IAM_ELEVATION, reason, action);
  }

  /** Same as {@link #allowIam(String, Supplier)} for an action without result. */
  public static void allowIam(String reason, Runnable action) {
    Objects.requireNonNull(action, "action");
    allowIam(reason, () -> {
      action.run();
      return null;
    });
  }

  /** True while the current thread runs inside {@link #allowIam(String, Supplier)}. */
  public static boolean isIamElevated() {
    return IAM_ELEVATION.get() != null;
  }

  /**
   * Switch of the super admin power of a first-party mobile token: inside the
   * call, {@link #allowsSuperAdmin()} is true for that token on the current
   * thread (restored after it, also when it throws), so
   * {@link PermissionService#isSuperAdmin()} answers from the user's roles.
   * Off by default: the caller decides where it applies (the app endpoints
   * only, for a user signed in with MFA). Each use is logged with its reason.
   * For any other token it only runs the action (a Console token already has
   * the power, a third-party one never gets it).
   *
   * @param reason why the power is opened, for the logs (required)
   */
  public static <T> T withSuperAdminPowers(String reason, Supplier<T> action) {
    Objects.requireNonNull(action, "action");
    requireReason(reason);
    var policy = current();
    if (!policy.isFirstPartyMobile()) {
      return action.get();
    }
    log.info("Super admin powers opened for a first-party mobile token: {} (client_id={}, sub={})", reason,
        policy.clientId, policy.subject);
    return runWith(SUPER_ADMIN_POWERS, reason, action);
  }

  /** Same as {@link #withSuperAdminPowers(String, Supplier)} for an action without result. */
  public static void withSuperAdminPowers(String reason, Runnable action) {
    Objects.requireNonNull(action, "action");
    withSuperAdminPowers(reason, () -> {
      action.run();
      return null;
    });
  }

  /**
   * The super admin check of {@link PermissionService#isSuperAdmin()} reads an
   * {@code iam.*} permission: for a first-party mobile token whose super admin
   * power is open, that one check runs with the IAM barrier lifted (not
   * logged: the opening of the power already is).
   */
  static boolean probeSuperAdmin(Supplier<Boolean> probe) {
    var policy = current();
    if (!policy.isFirstPartyMobile() || !policy.allowsSuperAdmin()) {
      return probe.get();
    }
    return runWith(IAM_ELEVATION, "super admin check", probe);
  }

  /**
   * The super admin check of {@link PermissionService#hasSuperAdminRole()}: the
   * roles of the user, whatever the power of the token. For a first-party
   * mobile token the {@code iam.*} barrier is lifted for that one check (not
   * logged: nothing is granted, the answer is only used to refuse).
   */
  static boolean probeSuperAdminRole(Supplier<Boolean> probe) {
    if (!current().isFirstPartyMobile()) {
      return probe.get();
    }
    return runWith(IAM_ELEVATION, "super admin role check", probe);
  }

  private static <T> T runWith(ThreadLocal<String> flag, String reason, Supplier<T> action) {
    var previous = flag.get();
    flag.set(reason);
    try {
      return action.get();
    } finally {
      if (previous == null) {
        flag.remove();
      } else {
        flag.set(previous); // nested call: the outer elevation goes on
      }
    }
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("An elevation needs a reason");
    }
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
