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
      r.urn,
      r.label,
      r.pub,
      r.schema_id,
      r.tenant_id,
      r.link_id,
      r.link_type,
      r.content,
      r.created_at,
      r.updated_at,
      r.created_by,
      r.updated_by
FROM  resource r
WHERE
      r.label = :label AND
      r.schema_id IN (SELECT id FROM tmp_schema_inheritance)
ORDER BY r.id
LIMIT 1