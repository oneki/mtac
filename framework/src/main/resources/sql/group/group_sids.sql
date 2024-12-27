WITH RECURSIVE hierarchy(group_id, member_group_id) AS (
  SELECT parent_id, child_id
    FROM group_membership WHERE child_id IN (:groupIds)
  UNION ALL
  SELECT gm.parent_id, gm.child_id
    FROM hierarchy h, group_membership gm
    WHERE h.group_id = gm.child_id
)
SELECT group_id FROM hierarchy limit 5000;
