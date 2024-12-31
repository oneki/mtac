WITH  "<placeholder for dynamic temporary tables />"
  tmp_tenant_ancestor AS (
    SELECT
      rtt.ancestor_id as id,
      rtt.depth as depth
    FROM
      resource_tenant_tree rtt
    WHERE
      rtt.descendant_id = :tenantId AND
      rtt.depth >= 0
  ),

  tmp_schema_inheritance AS (
    SELECT
      DISTINCT d.id as id
    FROM
      resource a
        JOIN "resource" sa ON a.id = sa.id AND sa.schema_id = 1,
      resource d
        JOIN "resource" sd ON d.id = sd.id AND sd.schema_id = 1,
      schema_inheritance si
    WHERE
      a.label = :schemaLabel AND
      si.descendant_id = d.id AND
      si.ancestor_id = a.id
  )
SELECT
  r.id,
  r.urn,
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
  l.urn as link_urn,
  l.content as link_content,
  l.pub as link_pub,
  l.tenant_id as link_tenant_id,
  l.created_at as link_created_at,
  l.updated_at as link_updated_at,
  l.created_by as link_created_by,
  l.updated_by as link_updated_by
FROM  resource r
        JOIN tmp_schema_inheritance tsi ON r.schema_id = tsi.id
        LEFT JOIN resource l ON r.link_id = l.id
        "<placeholder for dynamic left join tables />"
        "<placeholder for dynamic tables />"
WHERE
  (
    r.link_id IS NULL
    OR
      (
        NOT EXISTS (
          SELECT 1
            FROM resource_tenant_tree rtt
            WHERE
              rtt.ancestor_id = :tenantId and
              rtt.descendant_id = l.id 
        ) AND
        l.tenant_id NOT IN (SELECT id FROM tmp_tenant_ancestor WHERE depth > 0)
      )
  )
  AND
  (
    (
      r.tenant_id IN (SELECT id FROM tmp_tenant_ancestor WHERE depth > 0) AND
      r.pub IS TRUE
    )
    OR
    (
      EXISTS (
        SELECT 1
        FROM resource_tenant_tree rtt
        WHERE
          rtt.ancestor_id = :tenantId AND
          rtt.descendant_id = r.tenant_id
      )
    )
  )
"<placeholder for dynamic filters />"
GROUP BY r.id, l.id"<placeholder for dynamic groupby />"
"<placeholder for dynamic order />"
"<placeholder for dynamic limit />"
