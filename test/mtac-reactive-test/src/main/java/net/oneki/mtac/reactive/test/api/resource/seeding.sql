-- Create the role role_asset_admin
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('urn:root:iam.role:role_asset_admin', 'role_asset_admin', true, 4, null, 5, to_json('{"name":"Asset Administrator","schemas":["*"],"actions":["asset.*|*"],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');
