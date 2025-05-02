SELECT t.id, 
       t.label, 
       rtts.label as schema_label,
       JSON_AGG(jsonb_build_object('id', ro.id, 'role', ro.content, 'assigned', case when source_id is null then true else false end)) as roles
FROM ace a, 
	 resource r, 
	 resource s, 
	 resource ro,
	 resource_tenant_tree rtt,
	 resource t,
	 resource rtts
where -- a.source_id IS NULL AND
      a.resource_id = r.id AND
      r.schema_id = s.id AND
      rtt.descendant_id = r.id AND
      s."label" like 'tenant.%' and
      a.role_id = ro.id and
      rtt.ancestor_id = t.id and
      a.identity_id IN (:identityIds) and
      t.schema_id = rtts.id
group by t.id, t.label, rtts.label