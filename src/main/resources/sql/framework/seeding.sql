ALTER SEQUENCE resource_id_seq RESTART WITH 1;

-- Create the schema resource (always with id=1)
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'schema', true, null, null, null, to_json('{"name":{"en":"schema","fr":"schema","nl":"schema","de":"schema"},"parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');
-- As the schema is created with id=1, we can update this row to set the schema_id to 1
UPDATE resource SET schema_id=1 WHERE id=1;

-- Create le schema tenant
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'tenant', true, null, null, 1, to_json('{"name":{"en":"Tenant","fr":"Tenant","nl":"Tenant","de":"Tenant"},"parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create le schema tenant.root
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'tenant.root', true, null, null, 1, to_json('{"name":{"en":"root","fr":"root","nl":"root","de":"root"},"parents":[{"label":"tenant","id":2,"schema":"schema"}]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the tenant root
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'root', false, null, null, 3, to_json('{"name":{"en":"Root","fr":"Root","nl":"Root","de":"Root"}}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- As the tenant root is created, we can associated the schema to this tenant (id = 4)
UPDATE resource SET tenant_id=4 WHERE id<=3;

-- Create the schema iam.role
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'iam.role', true, 4, null, 1, to_json('{"name":{"en":"Role","fr":"Rôle","nl":"Role","de":"Role"},"parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the schema iam.identity
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'iam.identity', true, 4, null, 1, to_json('{"name":{"en":"Identity","fr":"Identity","nl":"Identity","de":"Identity"},"parents":[]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the schema iam.identity.user
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'iam.identity.user', true, 4, null, 1, to_json('{"name":{"en":"User","fr":"Utilisateur","nl":"User","de":"User"},"parents":[{"label":"iam.identity","id":6,"schema":"schema"}]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the schema iam.identity.group
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'iam.identity.group', true, 4, null, 1, to_json('{"name":{"en":"Group","fr":"Groupe","nl":"Group","de":"Group"},"parents":[{"label":"iam.identity","id":6,"schema":"schema"}]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the user root@local
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'root@local', false, 4, null, 7, to_json('{"password":"$2a$10$y3V99Oz9G9Vsg9xgfclZXOO8gABVuQJSlBh8/je2XmRzIpso6N4He"}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Create the group super-admins
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'super-admins', false, 4, null, 8, to_json('{}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Add root@local in group super-admins
INSERT INTO group_membership (parent_id, child_id) VALUES (10,9);

-- Create the role role_admin
INSERT INTO resource (urn, label, pub, tenant_id, link_id, schema_id, content, created_at, updated_at, created_by, updated_by)
VALUES (gen_random_uuid(), 'role_admin', true, 4, null, 5, to_json('{"name":{"en":"Administrator","fr":"Administrateur","nl":"Administrator","de":"Administrator"},"schemas":["*"],"actions":["*"],"fields":["*"]}'::JSON), NOW(), NOW(), 'root@local', 'root@local');

-- Grant role role_admin to group super-admins on tenant root
INSERT INTO ace(identity_id, resource_id, role_id)
VALUES (10,4,11);

