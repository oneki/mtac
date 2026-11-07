package net.oneki.mtac.resource.iam.oidc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.repository.AbstractRepository;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.util.resource.R;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.oidc.OidcClient;

/**
 * Storage of the iam.oidc.client resources. Every method is unsecure (no ACL
 * filtering): callers check the permissions themselves (super admin for the
 * management API) or run without a user (authorization and token endpoints,
 * dynamic registration, cleanup job).
 *
 * <p>
 * Only resources of the root tenant are returned: an OIDC client is global.
 */
@Repository
@RequiredArgsConstructor
public class OidcClientRepository extends AbstractRepository {
  private final ResourceRepository resourceRepository;

  /** The client with this client_id (label), or null. */
  public OidcClient findByClientId(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      return null;
    }
    var resource = resourceRepository.getByUniqueLabelUnsecure(clientId, OidcClient.class);
    return asRootClient(resource);
  }

  /** The client with this resource id, or null when the id belongs to something else. */
  public OidcClient findById(Integer id) {
    if (id == null) {
      return null;
    }
    Resource resource = resourceRepository.getByIdUnsecure(id, Resource.class);
    return asRootClient(resource);
  }

  public OidcClient findByUid(String uid) {
    if (uid == null || uid.isBlank()) {
      return null;
    }
    Integer id;
    try {
      id = Resource.fromUid(uid);
    } catch (RuntimeException e) {
      return null;
    }
    return findById(id);
  }

  public List<OidcClient> listAll() {
    var query = Query.builder()
        .tenant(Constants.TENANT_ROOT_ID)
        .limit(Constants.LIST_ALL_LIMIT)
        .build();
    var resources = resourceRepository.listByTenantAndTypeUnsecure(Constants.TENANT_ROOT_ID, OidcClient.class,
        query);
    return resources.stream()
        .map(this::asRootClient)
        .filter(client -> client != null)
        .toList();
  }

  public OidcClient create(OidcClient client) {
    R.fillMeta(client);
    return resourceRepository.create(client);
  }

  public void update(OidcClient client) {
    R.fillMeta(client);
    resourceRepository.update(client);
  }

  public void delete(Integer id) {
    resourceRepository.delete(id);
  }

  // lastUsedAt is written like the database mapper writes an Instant (ISO-8601, UTC)
  private static final String SQL_TOUCH_LAST_USED_AT = "UPDATE resource "
      + "SET content = jsonb_set(COALESCE(content, '{}'::jsonb), '{lastUsedAt}', to_jsonb(CAST(:last_used_at AS text)), true) "
      + "WHERE id = :id AND label = :label AND link_id IS NULL";

  /**
   * Sets lastUsedAt of a client, and nothing else: a full update would
   * overwrite an edit a super admin saves at the same moment. updated_at and
   * updated_by are left untouched, the field is technical.
   *
   * @param uid      uid of the client resource
   * @param clientId its label, checked as well so that a reused id never
   *                 touches another resource
   * @return false when no client matched
   */
  public boolean touchLastUsedAt(String uid, String clientId, Instant lastUsedAt) {
    if (uid == null || clientId == null || lastUsedAt == null) {
      return false;
    }
    Integer id;
    try {
      id = Resource.fromUid(uid);
    } catch (RuntimeException e) {
      return false;
    }
    var params = new HashMap<String, Object>();
    params.put("id", id);
    params.put("label", clientId);
    params.put("last_used_at", lastUsedAt.toString());
    return jdbcTemplate.update(SQL_TOUCH_LAST_USED_AT, params) == 1;
  }

  /**
   * Keeps only real clients of the root tenant: an id or a label of another
   * kind of resource, or a link, is never taken for a client.
   */
  private OidcClient asRootClient(Object resource) {
    if (!(resource instanceof OidcClient client)) {
      return null;
    }
    if (client.getLinkId() != null || client.isLink()) {
      return null;
    }
    if (client.getTenantId() != null && !Constants.TENANT_ROOT_ID.equals(client.getTenantId())) {
      return null;
    }
    return client;
  }
}
