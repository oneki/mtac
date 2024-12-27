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
FROM  resource r 
WHERE
      r.id = :id
LIMIT 1
     
