WITH tmp_schema_inheritance AS(
            SELECT DISTINCT d.id as id
            FROM resource a JOIN "resource" sa ON a.id = sa.id AND sa.schema_id = 1,
                  resource d JOIN "resource" sd ON d.id = sd.id AND sd.schema_id = 1,
                  schema_inheritance si
            WHERE a.label = :schemaLabel AND
                  si.descendant_id = d.id AND
                  si.ancestor_id = a.id
     )
SELECT
      r.id as id,
      r.public_id,
      r.label,
      r.pub,
      r.schema_id,
      r.tenant_id,
      r.link_id,
      r.content,
      r.created_at,
      r.updated_at,
      r.created_by,
      r.updated_by,
      l.id as link_id,
      l.public_id as link_public_id,
      l.content as link_content,
      l.pub as link_pub,
      l.tenant_id as link_tenant_id,
      l.created_at as link_created_at,
      l.updated_at as link_updated_at,
      l.created_by as link_created_by,
      l.updated_by as link_updated_by,      
      json_agg(ro.content) as acl
FROM  resource r
      LEFT JOIN resource l on r.link_id = l.id
      JOIN resource_ace ra 
          JOIN resource ro on ra.role_id = ro.id
      ON r.id = ra.resource_id AND ra.identity_id IN (:sids)
WHERE
      r.label = :label AND
      r.schema_id IN (SELECT id FROM tmp_schema_inheritance)
GROUP BY r.id, l.id
ORDER BY r.id
LIMIT 1