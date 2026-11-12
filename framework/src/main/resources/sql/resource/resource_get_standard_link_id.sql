SELECT r.id
FROM resource r
WHERE r.link_id = :linkId
  AND r.tenant_id = :tenantId
  AND r.link_type = 0
LIMIT 1;
