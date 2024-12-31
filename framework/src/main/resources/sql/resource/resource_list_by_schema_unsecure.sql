WITH "<placeholder for dynamic temporary tables />"
  tmp_schema_inheritance AS (
    SELECT
      DISTINCT d.id as id
    FROM
      resource a
        JOIN "resource" sa ON a.id = sa.id
        AND sa.schema_id = 1, -- schema_id = 1 means the resource is of type schema
      resource d
        JOIN "resource" sd ON d.id = sd.id
        AND sd.schema_id = 1, -- schema_id = 1 means the resource is of type schema
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
  l.label as link_label,
  l.content as link_content
FROM  resource r
        JOIN tmp_schema_inheritance tsi ON r.schema_id = tsi.id
        LEFT JOIN resource l ON r.link_id = l.id
        "<placeholder for dynamic left join tables />"
        "<placeholder for dynamic tables />"
WHERE
    r.link_id IS NULL
"<placeholder for dynamic filters />"
"<placeholder for dynamic groupby />"
"<placeholder for dynamic order />"
"<placeholder for dynamic limit />"
