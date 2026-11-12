INSERT INTO "resource" (
  label,
  pub,
  link_id,
  link_type,
  tenant_id,
  schema_id,
  created_by,
  resource_type,
  content
)
VALUES(
  :label,
  :pub,
  :linkId,
  0,
  :tenantId,
  :schemaId,
  :createdBy,
  :resourceType,
  to_json('{}'::JSON)
)
ON CONFLICT (link_id, tenant_id) WHERE link_type = 0 AND link_id IS NOT NULL DO NOTHING;
