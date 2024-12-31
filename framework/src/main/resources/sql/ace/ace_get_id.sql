SELECT id
FROM ace 
WHERE resource_id = :resourceId AND
      identity_id = :identityId AND 
      role_id = :roleId