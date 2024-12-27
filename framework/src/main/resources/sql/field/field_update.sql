UPDATE "field"
SET label = :label,
  "type" = :type,
  multiple  = :multiple,
  "required" = :required,
  validators = to_json(:validators::JSON),
  default_value = to_json(:defaultValue::JSON),
  priv = :priv,
  "description" = :description,
  editable = :editable,
  example = to_json(:example::JSON)
WHERE id = :id
