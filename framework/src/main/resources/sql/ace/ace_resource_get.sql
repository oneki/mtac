SELECT COUNT(*) 
FROM resource_ace 
WHERE identity_id IN (:identityIds) AND 
      resource_id = :resourceId