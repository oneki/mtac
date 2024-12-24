-- retrieve all roles that are directly assigned to the given tenant for a user
SELECT
      r.id as id,
      r.urn,
      r.label,
      r.pub,
      r.schema_id,
      r.tenant_id,
      r.link_id,
      r.content,
      r.created_at,
      r.updated_at,
      r.created_by,
      r.updated_by
FROM
	ace a,
	resource r,
	resource t
WHERE
	a.identity_id IN (:sids)
	AND a.resource_id = t.id
	AND t.id = :tenantId
	AND a.role_id = r.id
