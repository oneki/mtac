UPDATE "resource"
SET label = :label,
	pub = :pub,
	tenant_id = :tenantId,
	schema_id = :schemaId,
	content = to_json(:content::JSON),
	updated_at = NOW(),
  updated_by = :updatedBy
WHERE id = :id
