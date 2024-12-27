<UNIQUE_NAME>_tmp_tenant_ancestor AS (
      select rtt.ancestor_id as id, rtt.depth as depth
      FROM resource_tenant_tree rtt
      WHERE
        rtt.descendant_id = :tenantId AND
        rtt.depth >= 0
     ),
     <UNIQUE_NAME>_tmp_schema_inheritance AS(
        SELECT DISTINCT d.id as id
        FROM resource a JOIN resource sa ON a.id = sa.id AND sa.schema_id = :schemaSchemaId,
              resource d JOIN resource sd ON d.id = sd.id AND sd.schema_id = :schemaSchemaId,
              schema_inheritance si
        WHERE a.label = :<UNIQUE_NAME>SchemaLabel AND
              si.descendant_id = d.id AND
              si.ancestor_id = a.id
     ),
     <UNIQUE_NAME> AS(
            SELECT  r.id"<placeholder for dynamic relation order column />"
            FROM  resource r
                  JOIN <UNIQUE_NAME>_tmp_schema_inheritance tsi ON r.schema_id = tsi.id
                  LEFT JOIN resource l ON r.link_id = l.id
            "<placeholder for dynamic left join tables />"
            "<placeholder for dynamic tables />"
            WHERE 1 = 1
            "<placeholder for dynamic filters />"
     ),