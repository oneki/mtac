SELECT
  r.id,
  r.label,
  r.pub,
  r.schema_id,
  r.tenant_id,
  r.link_id,
  r.link_type,
  r.resource_type,
  l.id as link_id,
  l.tenant_id as link_tenant_id,
  l.pub as link_pub,
  l.resource_type as link_resource_type
FROM  resource r
        LEFT JOIN resource l ON r.link_id = l.id
        "<placeholder for dynamic left join tables />"
        "<placeholder for dynamic tables />"
WHERE
  r.link_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM resource_ace ra
    WHERE
      ra.identity_id IN (:sids) AND
      ra.resource_id = r.id
  )
  
"<placeholder for dynamic filters />"
GROUP BY r.id, l.id"<placeholder for dynamic groupby />"
"<placeholder for dynamic order />"
"<placeholder for dynamic limit />"
