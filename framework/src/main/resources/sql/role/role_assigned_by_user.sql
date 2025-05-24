SELECT t.id, t.label, t.schema_label, JSON_AGG(jsonb_build_object('id', ro.id, 'role', ro.content)) as roles
FROM ( 
      SELECT
            distinct rtt.ancestor_id as id, rttr.label as label, rtts.label as schema_label
            from 
                  resource_ace ra,
                  resource t,
                  resource s,
                  resource_tenant_tree rtt,
                  resource rttr,
                  resource rtts
            where
                  ra.resource_id = t.id and 
                  t.schema_id = s.id and
                  s.label like 'tenant.%' and
                  ra.identity_id in (:identityIds) and 
                  rtt.descendant_id = t.id	and 
                  rtt.ancestor_id = rttr.id and 
                  rttr.schema_id = rtts.id
      ) t left join 
            ace a join resource ro on a.role_id = ro.id and a.identity_id in (:identityIds) and a.source_id is null
      on t.id = a.resource_id
group by t.id, t.label, t.schema_label