DELETE FROM field
WHERE id IN (
  SELECT f.id
  FROM field f
  LEFT JOIN field_schema fs ON f.id = fs.field_id
  WHERE fs.field_id IS NULL
);