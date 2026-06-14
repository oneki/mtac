DELETE FROM field
WHERE id IN (
  SELECT f.id
  FROM field f
  WHERE NOT EXISTS (
    SELECT 1 
    FROM field_schema fs 
    WHERE fs.field_id = f.id
  )
);