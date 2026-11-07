package net.oneki.mtac.resource.iam.oidc;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.core.service.openid.oidc.OidcClientRegistry;
import net.oneki.mtac.core.service.security.PermissionService;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.OidcConsentRepository;
import net.oneki.mtac.framework.repository.OidcRepository;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.dto.UpsertResponse;
import net.oneki.mtac.model.core.openid.oidc.OidcClientKind;
import net.oneki.mtac.model.core.openid.oidc.OidcClientSource;
import net.oneki.mtac.model.core.resource.SearchDto;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.exception.ForbiddenException;
import net.oneki.mtac.model.core.util.exception.NotFoundException;
import net.oneki.mtac.model.framework.Page;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.oidc.OidcClient;
import net.oneki.mtac.model.resource.iam.oidc.OidcClientCreateResponse;
import net.oneki.mtac.model.resource.iam.oidc.OidcClientSecret;
import net.oneki.mtac.model.resource.iam.oidc.OidcClientSecretResponse;
import net.oneki.mtac.model.resource.iam.oidc.OidcClientUpsertRequest;
import net.oneki.mtac.resource.ResourceService;

/**
 * The iam.oidc.client resources: OpenID clients of the provider (authorization
 * code + PKCE). They are global and live in the root tenant.
 *
 * <p>
 * Two families of methods:
 * <ul>
 * <li>the management API (create, update, delete, get, list, secrets): every
 * method requires a super admin ({@link PermissionService#isSuperAdmin()}),
 * whatever the tenant of the request, and throws {@link ForbiddenException}
 * otherwise;</li>
 * <li>the {@code *Unsecure} methods, which run without a user: Dynamic Client
 * Registration ({@link #createDynamicUnsecure(OidcClient)}), the cleanup job
 * ({@link #listAllUnsecure()}, {@link #deleteByClientIdUnsecure(String)}) and
 * the provider ({@link #findByClientIdUnsecure(String)}).</li>
 * </ul>
 * Every change evicts the client from {@link OidcClientRegistry}. The secret
 * hashes are stored in the resource but never returned by the API (see
 * {@link OidcClient}).
 */
@Service("mtacOidcClientService")
@Slf4j
public class OidcClientService extends ResourceService<OidcClientUpsertRequest, OidcClient> {
  public static final String CLIENT_ID_PREFIX = "oc-";
  private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int CLIENT_ID_RANDOM_LENGTH = 20; // ~103 bits
  private static final int SECRET_ID_LENGTH = 8;
  private static final int SECRET_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  protected final OidcClientRepository clientRepository;
  protected final OidcClientRegistry clientRegistry;
  protected final OidcRepository oidcRepository;
  protected final OidcClientValidator validator;
  protected OidcConsentRepository consentRepository;

  @Autowired
  public OidcClientService(PermissionService permissionService, OidcClientRepository clientRepository,
      OidcClientRegistry clientRegistry, OidcRepository oidcRepository, OidcClientValidator validator) {
    this.permissionService = permissionService;
    this.clientRepository = clientRepository;
    this.clientRegistry = clientRegistry;
    this.oidcRepository = oidcRepository;
    this.validator = validator;
  }

  /** Setter injection: the subclasses keep their constructor. */
  @Autowired
  public void setConsentRepository(OidcConsentRepository consentRepository) {
    this.consentRepository = consentRepository;
  }

  @Override
  public Class<OidcClient> getEntityClass() {
    return OidcClient.class;
  }

  @Override
  public Class<OidcClientUpsertRequest> getRequestClass() {
    return OidcClientUpsertRequest.class;
  }

  // ================================================================ management API (super admin)

  /** Throws {@link ForbiddenException} unless the logged user is a super admin. */
  public void requireSuperAdmin() {
    if (permissionService == null || !permissionService.isSuperAdmin()) {
      throw new ForbiddenException("OIDC_CLIENT_FORBIDDEN", "Only a super admin can manage the OIDC clients");
    }
  }

  /**
   * Creates a client (source CONSOLE). The server generates the client_id and,
   * for a confidential client, a secret returned once in
   * {@link OidcClientCreateResponse#getClient_secret()}.
   */
  @Override
  public UpsertResponse<OidcClient> create(OidcClientUpsertRequest request) {
    requireSuperAdmin();
    if (request == null) {
      throw new BusinessException("OIDC_CLIENT_INVALID_REQUEST", "Missing request");
    }
    if (request.getKind() == null) {
      throw new BusinessException("OIDC_CLIENT_KIND_REQUIRED", "The kind of client is required");
    }
    var client = new OidcClient();
    client.setKind(request.getKind());
    client.setConfidential(request.getConfidential() != null ? request.getConfidential()
        : request.getKind() == OidcClientKind.WEB);
    applyRequest(client, request);
    client.setSource(OidcClientSource.CONSOLE);
    client.setSecrets(new ArrayList<>());

    String plainSecret = null;
    if (client.isConfidential()) {
      plainSecret = generateSecretValue();
      client.getSecrets().add(newSecret(plainSecret));
    }

    OidcClient created = null;
    var status = false;
    try {
      created = persistNew(client);
      status = true;
    } finally {
      auditRedacted(created != null ? created : client, null, Action.Create, status, false);
    }

    var response = new OidcClientCreateResponse();
    BeanUtils.copyProperties(copyOf(created), response, "link");
    response.setClient_id(created.getLabel());
    response.setClient_secret(plainSecret);
    return UpsertResponse.<OidcClient>builder()
        .status(UpsertResponse.Status.Success)
        .resource(response)
        .build();
  }

  /**
   * Updates a client. A field left null in the request keeps its value. The
   * client_id, the kind and the confidentiality cannot change; the secrets are
   * managed by {@link #addSecret(String)} and {@link #removeSecret(String, String)}.
   * Changing the redirect URIs revokes the client's refresh tokens.
   */
  @Override
  public UpsertResponse<OidcClient> update(String uid, OidcClientUpsertRequest request) {
    requireSuperAdmin();
    if (request == null) {
      throw new BusinessException("OIDC_CLIENT_INVALID_REQUEST", "Missing request");
    }
    var client = getClientOrThrow(uid);
    var before = copyOf(client);
    if (request.getLabel() != null && !request.getLabel().equals(client.getLabel())) {
      throw new BusinessException("OIDC_CLIENT_ID_IMMUTABLE", "The client_id cannot change");
    }
    if (request.getKind() != null && request.getKind() != client.getKind()) {
      throw new BusinessException("OIDC_CLIENT_KIND_IMMUTABLE", "The kind of client cannot change");
    }
    if (request.getConfidential() != null && request.getConfidential() != client.isConfidential()) {
      throw new BusinessException("OIDC_CLIENT_CONFIDENTIAL_IMMUTABLE",
          "A client cannot switch between public and confidential");
    }
    applyRequest(client, request);
    saveChanges(client, before, false);
    return UpsertResponse.<OidcClient>builder()
        .status(UpsertResponse.Status.Success)
        .resource(copyOf(client))
        .build();
  }

  @Override
  public UpsertResponse<Void> deleteByUid(String uid) {
    requireSuperAdmin();
    var client = getClientOrThrow(uid);
    return deleteClient(client, false);
  }

  @Override
  public UpsertResponse<Void> deleteById(Integer id) {
    requireSuperAdmin();
    var client = clientRepository.findById(id);
    if (client == null) {
      throw new NotFoundException("OIDC_CLIENT_NOT_FOUND", "OIDC client not found");
    }
    return deleteClient(client, false);
  }

  @Override
  public UpsertResponse<Void> delete(OidcClient resourceEntity) {
    requireSuperAdmin();
    if (resourceEntity == null || resourceEntity.getId() == null) {
      throw new NotFoundException("OIDC_CLIENT_NOT_FOUND", "OIDC client not found");
    }
    return deleteById(resourceEntity.getId());
  }

  /** Use {@link #create(OidcClientUpsertRequest)}: the client_id, secrets and source are set by the server. */
  @Override
  public UpsertResponse<OidcClient> create(OidcClient resourceEntity) {
    throw new UnsupportedOperationException("Use create(OidcClientUpsertRequest) or createDynamicUnsecure(OidcClient)");
  }

  @Override
  public UpsertResponse<OidcClient> createUnsecure(OidcClient resourceEntity) {
    throw new UnsupportedOperationException("Use createDynamicUnsecure(OidcClient)");
  }

  /** Use {@link #update(String, OidcClientUpsertRequest)}: it validates and evicts the registry cache. */
  @Override
  public UpsertResponse<OidcClient> update(OidcClient resourceEntity) {
    throw new UnsupportedOperationException("Use update(String, OidcClientUpsertRequest)");
  }

  @Override
  public UpsertResponse<OidcClient> updateUnsecure(OidcClient resourceEntity) {
    throw new UnsupportedOperationException("Use update(String, OidcClientUpsertRequest)");
  }

  @Override
  public UpsertResponse<Void> deleteLinkByUid(String linkUid) {
    throw new ForbiddenException("OIDC_CLIENT_FORBIDDEN", "An OIDC client has no link");
  }

  @Override
  public UpsertResponse<Void> deleteLinkById(Integer linkId) {
    throw new ForbiddenException("OIDC_CLIENT_FORBIDDEN", "An OIDC client has no link");
  }

  @Override
  public OidcClient getById(Integer id, Set<String> relations) {
    requireSuperAdmin();
    var client = clientRepository.findById(id);
    if (client == null) {
      throw new NotFoundException("OIDC_CLIENT_NOT_FOUND", "OIDC client not found");
    }
    return copyOf(client);
  }

  @Override
  public OidcClient getByUid(String uid) {
    return getByUid(uid, null);
  }

  @Override
  public OidcClient getByUid(String uid, Set<String> relations) {
    requireSuperAdmin();
    return copyOf(getClientOrThrow(uid));
  }

  @Override
  public OidcClientUpsertRequest getRequestById(Integer id, Set<String> relations) {
    return toUpsertRequest(getById(id, relations));
  }

  @Override
  public OidcClientUpsertRequest getRequestByUid(String uid) {
    return getRequestByUid(uid, null);
  }

  @Override
  public OidcClientUpsertRequest getRequestByUid(String uid, Set<String> relations) {
    return toUpsertRequest(getByUid(uid, relations));
  }

  /** Every client: they all live in the root tenant, whatever the tenant asked. */
  @Override
  public Page<OidcClient> list(Query query) {
    requireSuperAdmin();
    if (query == null) {
      query = new Query();
    }
    query.setTenant(Constants.TENANT_ROOT_ID);
    return redact(super.listUnsecure(query));
  }

  @Override
  public <C extends Resource> Page<C> list(Query query, Class<C> resultContentClass) {
    requireSuperAdmin();
    if (query == null) {
      query = new Query();
    }
    query.setTenant(Constants.TENANT_ROOT_ID);
    return redact(super.listUnsecure(query, resultContentClass));
  }

  @Override
  public Page<SearchDto> search(Query query) {
    requireSuperAdmin();
    return super.search(query);
  }

  /**
   * Adds a secret to a confidential client (rotation: at most two at a time)
   * and returns it in plain text, once.
   */
  public OidcClientSecretResponse addSecret(String uid) {
    requireSuperAdmin();
    var client = getClientOrThrow(uid);
    var before = copyOf(client);
    if (!client.isConfidential()) {
      throw new BusinessException("OIDC_CLIENT_NOT_CONFIDENTIAL", "A public client has no secret");
    }
    if (client.getSecrets().size() >= OidcClientValidator.MAX_SECRETS) {
      throw new BusinessException("OIDC_CLIENT_TOO_MANY_SECRETS",
          "A client cannot have more than " + OidcClientValidator.MAX_SECRETS
              + " secrets: remove the old one first");
    }
    var plainSecret = generateSecretValue();
    var secret = newSecret(plainSecret);
    client.getSecrets().add(secret);
    saveChanges(client, before, false);
    return OidcClientSecretResponse.builder()
        .clientId(client.getLabel())
        .clientSecret(plainSecret)
        .id(secret.getId())
        .hint(secret.getHint())
        .createdAt(secret.getCreatedAt())
        .build();
  }

  /** Removes a secret; the last secret of a confidential client cannot be removed. */
  public UpsertResponse<OidcClient> removeSecret(String uid, String secretId) {
    requireSuperAdmin();
    var client = getClientOrThrow(uid);
    var before = copyOf(client);
    var secret = client.getSecrets().stream()
        .filter(candidate -> candidate != null && Objects.equals(candidate.getId(), secretId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("OIDC_CLIENT_SECRET_NOT_FOUND", "Secret not found"));
    if (client.isConfidential() && client.getSecrets().size() <= 1) {
      throw new BusinessException("OIDC_CLIENT_LAST_SECRET",
          "The last secret of a confidential client cannot be removed: add a new one first");
    }
    client.getSecrets().remove(secret);
    saveChanges(client, before, false);
    return UpsertResponse.<OidcClient>builder()
        .status(UpsertResponse.Status.Success)
        .resource(copyOf(client))
        .build();
  }

  @Override
  public OidcClientUpsertRequest toUpsertRequest(OidcClient client) {
    if (client == null) {
      return null;
    }
    var request = new OidcClientUpsertRequest();
    request.setLabel(client.getLabel());
    request.setTenant(Resource.toUid(Constants.TENANT_ROOT_ID));
    request.setName(client.getName());
    request.setDescription(client.getDescription());
    request.setKind(client.getKind());
    request.setConfidential(client.isConfidential());
    request.setRedirectUris(new ArrayList<>(client.getRedirectUris()));
    request.setPostLogoutRedirectUris(new ArrayList<>(client.getPostLogoutRedirectUris()));
    request.setAllowedOrigins(new ArrayList<>(client.getAllowedOrigins()));
    request.setLoopback(client.isLoopback());
    request.setScopes(new ArrayList<>(client.getScopes()));
    request.setRefreshToken(client.isRefreshToken());
    request.setRequireMfa(client.isRequireMfa());
    request.setConsentMode(client.getConsentMode());
    request.setAccessMode(client.getAccessMode());
    request.setResources(new ArrayList<>(client.getResources()));
    request.setSessionMaxDays(client.getSessionMaxDays());
    return request;
  }

  // ================================================================ unsecure (no user context)

  /**
   * Creates a client without a user, for Dynamic Client Registration (step D3).
   * The caller builds the whole client, with its forced policy (consent mode,
   * access mode, resources, scopes); this method generates the client_id
   * (any label set is replaced), forces {@code source = DCR} and the root
   * tenant, refuses a confidential client (DCR registers public clients only)
   * and applies the same validation as the management API (so TRUSTED is
   * refused). Nothing is audited.
   *
   * @return the stored client, with its id, uid and client_id (label)
   */
  public OidcClient createDynamicUnsecure(OidcClient client) {
    if (client == null) {
      throw new BusinessException("OIDC_CLIENT_INVALID_REQUEST", "Missing client");
    }
    if (client.isConfidential()) {
      throw new BusinessException("OIDC_CLIENT_CONFIDENTIAL_NOT_ALLOWED",
          "A dynamically registered client must be public");
    }
    client.setId(null);
    client.setUid(null);
    client.setLabel(null);
    client.setSource(OidcClientSource.DCR);
    client.setSecrets(new ArrayList<>());
    return persistNew(client);
  }

  public OidcClient findByClientIdUnsecure(String clientId) {
    return clientRepository.findByClientId(clientId);
  }

  public List<OidcClient> listAllUnsecure() {
    return clientRepository.listAll();
  }

  /** Deletes a client without a user (cleanup of unused DCR clients). */
  public void deleteByClientIdUnsecure(String clientId) {
    var client = clientRepository.findByClientId(clientId);
    if (client != null) {
      deleteClient(client, true);
    }
  }

  @Override
  public UpsertResponse<Void> deleteByIdUnsecure(Integer id) {
    var client = clientRepository.findById(id);
    if (client != null) {
      deleteClient(client, true);
    }
    return UpsertResponse.<Void>builder()
        .status(UpsertResponse.Status.Success)
        .build();
  }

  @Override
  public UpsertResponse<Void> deleteByIdsUnsecure(List<Integer> ids) {
    if (ids != null) {
      ids.forEach(this::deleteByIdUnsecure);
    }
    return UpsertResponse.<Void>builder()
        .status(UpsertResponse.Status.Success)
        .build();
  }

  // ================================================================ hooks

  /**
   * Called when the client is deleted or its redirect URIs change: whatever it
   * was granted must stop working. Revokes its refresh tokens, drops its
   * pending authorization codes and deletes the consents of its users (they are
   * asked again, with the new redirect URIs shown on the consent page).
   */
  protected void onClientRevoked(String clientId) {
    var now = Instant.now();
    var revoked = oidcRepository.revokeRefreshTokensOfClient(clientId, now);
    oidcRepository.deleteUnusedAuthorizationCodesOfClient(clientId);
    var consents = consentRepository != null ? consentRepository.deleteByClient(clientId) : 0;
    log.info("OIDC client {}: {} refresh token(s) revoked, {} consent(s) deleted", clientId, revoked, consents);
  }

  // ================================================================ internals

  /** Copies the non-null fields of the request (the secrets, source and client_id are never taken). */
  protected void applyRequest(OidcClient client, OidcClientUpsertRequest request) {
    if (request.getName() != null) {
      client.setName(request.getName());
    }
    if (request.getDescription() != null) {
      client.setDescription(request.getDescription());
    }
    if (request.getRedirectUris() != null) {
      client.setRedirectUris(new ArrayList<>(request.getRedirectUris()));
    }
    if (request.getPostLogoutRedirectUris() != null) {
      client.setPostLogoutRedirectUris(new ArrayList<>(request.getPostLogoutRedirectUris()));
    }
    if (request.getAllowedOrigins() != null) {
      client.setAllowedOrigins(new ArrayList<>(request.getAllowedOrigins()));
    }
    if (request.getLoopback() != null) {
      client.setLoopback(request.getLoopback());
    }
    if (request.getScopes() != null) {
      client.setScopes(new ArrayList<>(request.getScopes()));
    } else if (client.getScopes() == null || client.getScopes().isEmpty()) {
      client.setScopes(new ArrayList<>(List.of(OidcClientValidator.SCOPE_OPENID)));
    }
    if (request.getRefreshToken() != null) {
      client.setRefreshToken(request.getRefreshToken());
    }
    if (request.getRequireMfa() != null) {
      client.setRequireMfa(request.getRequireMfa());
    }
    if (request.getConsentMode() != null) {
      client.setConsentMode(request.getConsentMode());
    }
    if (request.getAccessMode() != null) {
      client.setAccessMode(request.getAccessMode());
    }
    if (request.getResources() != null) {
      client.setResources(new ArrayList<>(request.getResources()));
    }
    if (request.getSessionMaxDays() != null) {
      client.setSessionMaxDays(request.getSessionMaxDays());
    }
  }

  /** Generates the client_id, validates and stores a new client in the root tenant. */
  protected OidcClient persistNew(OidcClient client) {
    client.setTenantId(Constants.TENANT_ROOT_ID);
    client.setTenantLabel(Constants.TENANT_ROOT_LABEL);
    client.setLabel(generateClientId());
    validator.validate(client);
    var created = clientRepository.create(client);
    if (created == null) {
      throw new BusinessException("OIDC_CLIENT_NOT_CREATED", "The OIDC client could not be created");
    }
    clientRegistry.evict(created.getLabel());
    return created;
  }

  protected void saveChanges(OidcClient client, OidcClient before, boolean unsecure) {
    var status = false;
    try {
      validator.validate(client);
      if (!unsecure && securityContext != null) {
        client.setUpdatedBy(securityContext.getUsername());
      }
      clientRepository.update(client);
      clientRegistry.evict(client.getLabel());
      status = true;
    } finally {
      auditRedacted(client, before, Action.Update, status, unsecure);
    }
    if (!Objects.equals(before.getRedirectUris(), client.getRedirectUris())) {
      onClientRevoked(client.getLabel());
    }
  }

  protected UpsertResponse<Void> deleteClient(OidcClient client, boolean unsecure) {
    var status = false;
    try {
      clientRepository.delete(client.getId());
      clientRegistry.evict(client.getLabel());
      status = true;
    } finally {
      auditRedacted(client, null, Action.Delete, status, unsecure);
    }
    onClientRevoked(client.getLabel());
    return UpsertResponse.<Void>builder()
        .status(UpsertResponse.Status.Success)
        .build();
  }

  /** Hands the audit log copies without secret hashes. */
  protected void auditRedacted(OidcClient newClient, OidcClient oldClient, Action action, boolean status,
      boolean unsecure) {
    try {
      audit(newClient != null ? copyOf(newClient) : null, oldClient != null ? copyOf(oldClient) : null, action,
          status, unsecure);
    } catch (RuntimeException e) {
      log.error("Cannot audit the OIDC client {}: {}", newClient != null ? newClient.getLabel() : null,
          e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  protected <C extends Resource> Page<C> redact(Page<C> page) {
    if (page != null && page.getData() != null) {
      page.setData(page.getData().stream()
          .map(resource -> resource instanceof OidcClient client ? (C) copyOf(client) : resource)
          .toList());
    }
    return page;
  }

  protected OidcClient getClientOrThrow(String uid) {
    var client = clientRepository.findByUid(uid);
    if (client == null) {
      throw new NotFoundException("OIDC_CLIENT_NOT_FOUND", "OIDC client not found");
    }
    if (client.getSecrets() == null) {
      client.setSecrets(new ArrayList<>());
    }
    return client;
  }

  protected String generateClientId() {
    for (var attempt = 0; attempt < 5; attempt++) {
      var clientId = CLIENT_ID_PREFIX + randomString(CLIENT_ID_RANDOM_LENGTH);
      try {
        validator.validateClientId(clientId, clientRegistry);
      } catch (BusinessException e) {
        continue;
      }
      if (clientRepository.findByClientId(clientId) == null) {
        return clientId;
      }
    }
    throw new BusinessException("OIDC_CLIENT_ID_GENERATION_FAILED", "Cannot generate a unique client_id");
  }

  protected OidcClientSecret newSecret(String plainSecret) {
    return OidcClientSecret.builder()
        .id(randomString(SECRET_ID_LENGTH))
        .hash(PasswordUtil.hash(plainSecret))
        .hint(plainSecret.substring(plainSecret.length() - 4))
        .createdAt(Instant.now())
        .build();
  }

  /**
   * 256 random bits, base64url without padding: only {@code [A-Za-z0-9_-]}, so
   * the secret survives form and HTTP Basic encodings even when a client does
   * not URL-encode it.
   */
  protected String generateSecretValue() {
    var bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String randomString(int length) {
    var builder = new StringBuilder(length);
    for (var i = 0; i < length; i++) {
      builder.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
    }
    return builder.toString();
  }

  /**
   * Shallow copy whose secrets carry no hash: used as the "before" state of an
   * update and safe to hand to an audit log.
   */
  public static OidcClient copyOf(OidcClient client) {
    var copy = new OidcClient();
    // link is derived from linkId and its setter refuses any call
    BeanUtils.copyProperties(client, copy, "link");
    copy.setRedirectUris(copyList(client.getRedirectUris()));
    copy.setPostLogoutRedirectUris(copyList(client.getPostLogoutRedirectUris()));
    copy.setAllowedOrigins(copyList(client.getAllowedOrigins()));
    copy.setScopes(copyList(client.getScopes()));
    copy.setResources(copyList(client.getResources()));
    copy.setSecrets(withoutHashes(client.getSecrets()));
    return copy;
  }

  private static List<String> copyList(List<String> values) {
    return values != null ? new ArrayList<>(values) : new ArrayList<>();
  }

  public static List<OidcClientSecret> withoutHashes(List<OidcClientSecret> secrets) {
    var result = new ArrayList<OidcClientSecret>();
    if (secrets != null) {
      for (var secret : secrets) {
        if (secret != null) {
          result.add(OidcClientSecret.builder()
              .id(secret.getId())
              .hint(secret.getHint())
              .createdAt(secret.getCreatedAt())
              .build());
        }
      }
    }
    return result;
  }
}
