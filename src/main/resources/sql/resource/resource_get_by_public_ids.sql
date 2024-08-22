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
      (r.public_id IN (:publicIds) OR l.public_id IN (:publicIds))
GROUP BY r.id, l.id
ORDER BY r.id