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
          FROM resource_ace ra
          WHERE
            ra.identity_id IN (:sids) AND
            ra.resource_id = l.id AND
            EXISTS (
              SELECT 1
              FROM resource_tenant_tree rtt
              WHERE
                rtt.ancestor_id = :tenantId and
                rtt.descendant_id = l.id
            ) AND
            l.tenant_id NOT IN (SELECT id FROM tmp_tenant_ancestor WHERE depth > 0)
        )
        OR
        (
          l.pub IS TRUE AND
          l.tenant_id IN (SELECT id FROM tmp_tenant_ancestor WHERE depth > 0)
        )
      )
  )
  AND
  (
    (
      r.tenant_id IN (SELECT id FROM tmp_tenant_ancestor WHERE depth > 0) AND
      r.pub IS TRUE AND
      (
        r.tenant_id IN (:tenantSids) OR
        EXISTS (
          SELECT 1
          FROM resource_ace ra
          WHERE
            ra.identity_id IN (:sids) AND
            ra.resource_id = r.id
        )
      )
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
      AND
      (
        EXISTS (
          SELECT 1
          FROM resource_ace ra
          WHERE
            ra.identity_id IN (:sids) AND
            ra.resource_id = r.id
        )
        OR
        (
          r.pub IS TRUE AND
          r.tenant_id IN (:tenantSids)
        )
      )
    )
  )
"<placeholder for dynamic filters />"
GROUP BY r.id, l.id"<placeholder for dynamic groupby />"
"<placeholder for dynamic order />"
"<placeholder for dynamic limit />"
