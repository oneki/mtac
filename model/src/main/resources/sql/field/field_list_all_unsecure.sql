SELECT
	f.*,
    json_agg(s.label) as schemas
FROM
	field f, field_schema fs, resource s
WHERE
	s.schema_id = 1
    AND fs.schema_id = s.id
    AND f.id = fs.field_id
GROUP BY f.id