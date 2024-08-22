SELECT
	r.id,
    r.public_id,
    r.label,
    r.pub,
    r.schema_id,
    r.tenant_id,
    r.link_id,
    r.created_at,
    r.updated_at,
    r.created_by,
    r.updated_by,
    r.content
FROM
	resource r
WHERE
	r.schema_id = 1