INSERT INTO "resource" (
  label,
  pub,
  tenant_id,
  schema_id,
  created_by,
  updated_by,
  content
)
VALUES(
  :label,
  :pub,
  :tenantId,
  :schemaId,
  :createdBy,
  :updatedBy,
  to_json(:content::JSON)
);