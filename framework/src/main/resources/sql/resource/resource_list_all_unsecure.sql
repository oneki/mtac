SELECT
  r.id,
  r.label,
  r.pub,
  r.schema_id,
  r.tenant_id,
  r.link_id,
      r.link_type,
  r.created_at,
  r.updated_at,
  r.created_by,
  r.updated_by,
  r.content,
  l.id as link_id,
  l.content as link_content,
  l.pub as link_pub,
  l.tenant_id as link_tenant_id,
  l.created_at as link_created_at,
  l.updated_at as link_updated_at,
  l.created_by as link_created_by,
  l.updated_by as link_updated_by
FROM  resource r
        LEFT JOIN resource l ON r.link_id = l.id
        "<placeholder for dynamic left join tables />"
        "<placeholder for dynamic tables />"
WHERE
  r.link_id IS NULL
"<placeholder for dynamic filters />"
GROUP BY r.id, l.id"<placeholder for dynamic groupby />"
"<placeholder for dynamic order />"
"<placeholder for dynamic limit />"
