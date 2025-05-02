ALTER SEQUENCE resource_id_seq RESTART WITH 1;

-- Create the schema resource (always with id=1)
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('schema', true, null, null, null, to_json('{"name":"schema","parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');
-- As the schema is created with id=1, we can update this row to set the schema_id to 1
UPDATE resource SET schema_id=1 WHERE id=1;

-- Create le schema tenant
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('tenant', true, null, null, 1, to_json('{"name":"Tenant","parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create le schema tenant.root
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('tenant.root', true, null, null, 1, to_json('{"parents":[{"l":"tenant","id":2,"s":1}]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the tenant root
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('root', false, null, null, 3, to_json('{}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- As the tenant root is created, we can associated the schema to this tenant (id = 4)
UPDATE resource SET tenant_id=4 WHERE id<=3;

-- Create the schema iam.role
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('iam.role', true, 4, null, 1, to_json('{"name":"Role","parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the schema iam.identity
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('iam.identity', true, 4, null, 1, to_json('{"name":"Identity","parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the schema iam.identity.user
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('iam.identity.user', true, 4, null, 1, to_json('{"name":"User","parents":[{"l":"iam.identity","id":6,"s":1}]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the schema iam.identity.group
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ( 'iam.identity.group', true, 4, null, 1, to_json('{"name":"Group","parents":[{"l":"iam.identity","id":6,"s":1}]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the user root@local
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('root@local', false, 4, null, 7, to_json('{"password":"$2a$10$y3V99Oz9G9Vsg9xgfclZXOO8gABVuQJSlBh8/je2XmRzIpso6N4He"}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the group super-admins
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('super-admins', false, 4, null, 8, to_json('{}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Add root@local in group super-admins
INSERT INTO group_membership (parent_id, child_id) VALUES (10,9);

-- Create the role role_admin
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('admin', true, 4, null, 5, to_json('{"name":"Administrator","schemas":["*"],"actions":["*"],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the role role_group_admin
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('group-admin', true, 4, null, 5, to_json('{"name":"Group Administrator","schemas":["*"],"actions":["iam.identity.group|*"],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the role role_user_admin
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('user-admin', true, 4, null, 5, to_json('{"name":"User Administrator","schemas":["*"],"actions":["iam.identity.user|*"],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the role iam_admin
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('iam-admin', true, 4, null, 5, to_json('{"name":"IAM Administrator","schemas":["*"],"actions":["iam.*|*"],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the role role_viewer
INSERT INTO resource (label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES ('readonly', true, 4, null, 5, to_json('{"name":"Read only","schemas":["*"],"actions":[],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Grant role role_admin to group super-admins on tenant root
INSERT INTO ace(identity_id, resource_id, role_id)
VALUES (10,4,11);

