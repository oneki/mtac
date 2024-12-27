SELECT DISTINCT(rtt.ancestor_id)
FROM ace a, 
	 resource r, 
	 resource s, 
	 resource_tenant_tree rtt
where a.source_id IS NULL AND
      a.resource_id = r.id AND
      r.schema_id = s.id AND
      rtt.descendant_id = r.id AND
      s."label" like 'tenant.%' AND
      a.identity_id IN (:userSids)