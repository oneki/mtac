-- Database generated with pgModeler (PostgreSQL Database Modeler).
-- pgModeler version: 1.1.0
-- PostgreSQL version: 16.0
-- Project Site: pgmodeler.io
-- Model Author: ---

-- Database creation must be performed outside a multi lined SQL file. 
-- These commands were put in this file only as a convenience.
-- 
-- object: registry | type: DATABASE --
-- DROP DATABASE IF EXISTS registry;
-- CREATE DATABASE registry
-- 	TABLESPACE = pg_default
-- 	OWNER = postgres;
-- ddl-end --

-- Appended SQL commands --
SELECT now();
-- ddl-end --


SET check_function_bodies = false;
-- ddl-end --

-- object: public.schema_inheritance_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.schema_inheritance_id_seq CASCADE;
CREATE SEQUENCE public.schema_inheritance_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.schema_inheritance_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.resource_tenant_tree | type: TABLE --
-- DROP TABLE IF EXISTS public.resource_tenant_tree CASCADE;
CREATE TABLE public.resource_tenant_tree (
	ancestor_id integer NOT NULL,
	descendant_id integer NOT NULL,
	depth integer NOT NULL

);
-- ddl-end --
ALTER TABLE public.resource_tenant_tree OWNER TO postgres;
-- ddl-end --

-- object: public.schema_inheritance | type: TABLE --
-- DROP TABLE IF EXISTS public.schema_inheritance CASCADE;
CREATE TABLE public.schema_inheritance (
	id integer NOT NULL DEFAULT nextval('public.schema_inheritance_id_seq'::regclass),
	ancestor_id integer NOT NULL,
	descendant_id integer NOT NULL,
	depth integer NOT NULL,
	schema_parent_id integer,
	CONSTRAINT schema_inheritance_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.schema_inheritance OWNER TO postgres;
-- ddl-end --

-- object: public.field_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.field_id_seq CASCADE;
CREATE SEQUENCE public.field_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.field_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.action_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.action_id_seq CASCADE;
CREATE SEQUENCE public.action_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.action_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.role_field_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.role_field_id_seq CASCADE;
CREATE SEQUENCE public.role_field_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.role_field_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.role_action_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.role_action_id_seq CASCADE;
CREATE SEQUENCE public.role_action_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.role_action_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.role_schema | type: TABLE --
-- DROP TABLE IF EXISTS public.role_schema CASCADE;
CREATE TABLE public.role_schema (
	role_id integer NOT NULL,
	schema varchar NOT NULL,
	CONSTRAINT role_schema_pk PRIMARY KEY (role_id,schema)
);
-- ddl-end --
ALTER TABLE public.role_schema OWNER TO postgres;
-- ddl-end --

-- object: public.ace_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.ace_id_seq CASCADE;
CREATE SEQUENCE public.ace_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.ace_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.resource_ace | type: TABLE --
-- DROP TABLE IF EXISTS public.resource_ace CASCADE;
CREATE TABLE public.resource_ace (
	ace_id integer NOT NULL,
	identity_id integer,
	resource_id integer,
	role_id integer,
	CONSTRAINT resource_ace_pk PRIMARY KEY (ace_id)
);
-- ddl-end --
ALTER TABLE public.resource_ace OWNER TO postgres;
-- ddl-end --

-- object: resource_tenant_tree_descendant_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_tenant_tree_descendant_idx CASCADE;
CREATE INDEX resource_tenant_tree_descendant_idx ON public.resource_tenant_tree
USING btree
(
	descendant_id
);
-- ddl-end --

-- object: schema_inheritance_descendant_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.schema_inheritance_descendant_idx CASCADE;
CREATE INDEX schema_inheritance_descendant_idx ON public.schema_inheritance
USING btree
(
	descendant_id
);
-- ddl-end --

-- object: public.group_membership | type: TABLE --
-- DROP TABLE IF EXISTS public.group_membership CASCADE;
CREATE TABLE public.group_membership (
	parent_id integer NOT NULL,
	child_id integer NOT NULL,
	CONSTRAINT group_membership_pk PRIMARY KEY (parent_id,child_id)
);
-- ddl-end --
ALTER TABLE public.group_membership OWNER TO postgres;
-- ddl-end --

-- object: public.resource_tag | type: TABLE --
-- DROP TABLE IF EXISTS public.resource_tag CASCADE;
CREATE TABLE public.resource_tag (
	resource_id integer NOT NULL,
	tag_id integer NOT NULL,
	CONSTRAINT resource_tag_pk PRIMARY KEY (resource_id,tag_id)
);
-- ddl-end --
ALTER TABLE public.resource_tag OWNER TO postgres;
-- ddl-end --

-- object: resource_tenant_tree_ascendant_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_tenant_tree_ascendant_idx CASCADE;
CREATE INDEX resource_tenant_tree_ascendant_idx ON public.resource_tenant_tree
USING btree
(
	ancestor_id
);
-- ddl-end --

-- object: schema_inheritance_ascendant_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.schema_inheritance_ascendant_idx CASCADE;
CREATE INDEX schema_inheritance_ascendant_idx ON public.schema_inheritance
USING btree
(
	ancestor_id
);
-- ddl-end --

-- object: public.role_tag | type: TABLE --
-- DROP TABLE IF EXISTS public.role_tag CASCADE;
CREATE TABLE public.role_tag (
	role_id integer NOT NULL,
	tag varchar NOT NULL,
	CONSTRAINT role_tag_pk PRIMARY KEY (role_id,tag)
);
-- ddl-end --
ALTER TABLE public.role_tag OWNER TO postgres;
-- ddl-end --

-- object: resource_ace_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_ace_idx CASCADE;
CREATE INDEX resource_ace_idx ON public.resource_ace
USING btree
(
	identity_id,
	resource_id,
	role_id
);
-- ddl-end --

-- object: public.ace | type: TABLE --
-- DROP TABLE IF EXISTS public.ace CASCADE;
CREATE TABLE public.ace (
	id integer NOT NULL DEFAULT nextval('public.ace_id_seq'::regclass),
	identity_id integer,
	resource_id integer,
	role_id integer,
	source_id integer,
	CONSTRAINT ace_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.ace OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_resource | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_resource() CASCADE;
CREATE FUNCTION public.after_delete_resource ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	DELETE FROM resource_tenant_tree
	    WHERE descendant_id IN (
	      SELECT descendant_id FROM resource_tenant_tree WHERE ancestor_id = OLD.id
	    );

    --------------------------------------------
    -- Add entry in audit log
    --------------------------------------------
	-- INSERT INTO audit_log(resource_id, "before", "user", "created_at", "action")
    -- SELECT OLD.id, OLD.content ||  jsonb_build_object('label', OLD.label , 'schema', OLD.schema_id, 'tenant', OLD.tenant_id, 'public', OLD.pub, 'link', OLD.link_id), OLD.updated_by, OLD.updated_at, 'delete'; 

    PERFORM pg_notify('resource_channel', 'delete,' || OLD.id || ',' || OLD.tenant_id || ',' || OLD.schema_id || ',' || OLD.label);

    RETURN NULL;
END;

$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_resource() OWNER TO postgres;
-- ddl-end --

-- object: public.after_add_or_delete_resource_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_add_or_delete_resource_tag(public.resource_tag) CASCADE;
CREATE FUNCTION public.after_add_or_delete_resource_tag (v_resource_tag public.resource_tag)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	-- check all ace related to resource and sync ace
	DROP TABLE IF EXISTS temp_after_add_or_delete_resource_tag;
	CREATE TEMP TABLE temp_after_add_or_delete_resource_tag AS
	SELECT DISTINCT ace.*
	FROM ace, resource r
	WHERE ace.resource_id = v_resource_tag.resource_id;
	PERFORM sync_resource_aces('temp_after_add_or_delete_resource_tag'::regclass);
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_add_or_delete_resource_tag(public.resource_tag) OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_resource_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_resource_tag() CASCADE;
CREATE FUNCTION public.after_insert_resource_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	PERFORM after_add_or_delete_resource_tag(NEW);
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_resource_tag() OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_resource_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_resource_tag() CASCADE;
CREATE FUNCTION public.after_delete_resource_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	PERFORM after_add_or_delete_resource_tag(OLD);
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_resource_tag() OWNER TO postgres;
-- ddl-end --

-- object: after_insert_resource_tag | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_resource_tag ON public.resource_tag CASCADE;
CREATE TRIGGER after_insert_resource_tag
	AFTER INSERT 
	ON public.resource_tag
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_resource_tag();
-- ddl-end --

-- object: after_delete_resource_tag | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_resource_tag ON public.resource_tag CASCADE;
CREATE TRIGGER after_delete_resource_tag
	AFTER DELETE 
	ON public.resource_tag
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_resource_tag();
-- ddl-end --

-- object: public.after_add_or_delete_role_schema | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_add_or_delete_role_schema(public.role_schema) CASCADE;
CREATE FUNCTION public.after_add_or_delete_role_schema (v_role_schema public.role_schema)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	DROP TABLE IF EXISTS temp_after_add_or_delete_role_schema;
    CREATE TEMP TABLE temp_after_add_or_delete_role_schema AS
        SELECT a.*
        FROM ace a, resource r, resource s
        WHERE a.resource_id = r.id
            AND a.role_id = v_role_schema.role_id
			AND r.schema_id = s.id
			AND s.label LIKE REPLACE(v_role_schema."schema", '*', '%');
	PERFORM sync_resource_aces('temp_after_add_or_delete_role_schema'::regclass);
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_add_or_delete_role_schema(public.role_schema) OWNER TO postgres;
-- ddl-end --

-- object: public.after_add_role_schema | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_add_role_schema() CASCADE;
CREATE FUNCTION public.after_add_role_schema ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	PERFORM after_add_or_delete_role_schema(NEW);
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_add_role_schema() OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_role_schema | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_role_schema() CASCADE;
CREATE FUNCTION public.after_delete_role_schema ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	PERFORM after_add_or_delete_role_schema(OLD);
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_role_schema() OWNER TO postgres;
-- ddl-end --

-- object: after_add_role_schema | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_add_role_schema ON public.role_schema CASCADE;
CREATE TRIGGER after_add_role_schema
	AFTER INSERT 
	ON public.role_schema
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_add_role_schema();
-- ddl-end --

-- object: after_delete_role_schema | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_role_schema ON public.role_schema CASCADE;
CREATE TRIGGER after_delete_role_schema
	AFTER DELETE 
	ON public.role_schema
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_role_schema();
-- ddl-end --

-- object: public.after_add_or_delete_role_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_add_or_delete_role_tag(public.role_tag) CASCADE;
CREATE FUNCTION public.after_add_or_delete_role_tag (v_role_tag public.role_tag)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
    DROP TABLE IF EXISTS temp_after_add_or_delete_role_tag;
    CREATE TEMP TABLE temp_after_add_or_delete_role_tag AS
        SELECT *
        FROM ace
        WHERE role_id = v_role_tag.role_id;
		PERFORM sync_resource_aces('temp_after_add_or_delete_role_tag'::regclass);
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_add_or_delete_role_tag(public.role_tag) OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_role_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_role_tag() CASCADE;
CREATE FUNCTION public.after_insert_role_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
    PERFORM after_add_or_delete_role_tag(NEW);
    RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_role_tag() OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_role_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_role_tag() CASCADE;
CREATE FUNCTION public.after_delete_role_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
    PERFORM after_add_or_delete_role_tag(OLD);
    RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_role_tag() OWNER TO postgres;
-- ddl-end --

-- object: after_insert_role_tag | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_role_tag ON public.role_tag CASCADE;
CREATE TRIGGER after_insert_role_tag
	AFTER INSERT 
	ON public.role_tag
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_role_tag();
-- ddl-end --

-- object: after_delete_role_tag | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_role_tag ON public.role_tag CASCADE;
CREATE TRIGGER after_delete_role_tag
	AFTER DELETE 
	ON public.role_tag
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_role_tag();
-- ddl-end --

-- object: public.after_add_or_delete_schema_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_add_or_delete_schema_tag(public.resource_tag) CASCADE;
CREATE FUNCTION public.after_add_or_delete_schema_tag (v_schema_tag public.resource_tag)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
	v_count integer;
BEGIN

	SELECT COUNT(*) INTO v_count FROM "schema" WHERE id = v_schema_tag.resource_id;
	
	IF v_count > 0 THEN
		-- check all ace related to schema and sync ace
		DROP TABLE IF EXISTS temp_after_insert_schema_tag;
		CREATE TEMP TABLE temp_after_insert_schema_tag AS
		SELECT DISTINCT ace.*
		FROM ace, resource r
		WHERE ace.resource_id = r.id AND
					r.schema_id = v_schema_tag.resource_id;
		PERFORM sync_resource_aces('temp_after_insert_schema_tag'::regclass);
	END IF;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_add_or_delete_schema_tag(public.resource_tag) OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_schema_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_schema_tag() CASCADE;
CREATE FUNCTION public.after_insert_schema_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	PERFORM after_add_or_delete_schema_tag(NEW);
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_schema_tag() OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_schema_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_schema_tag() CASCADE;
CREATE FUNCTION public.after_delete_schema_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	PERFORM after_add_or_delete_schema_tag(OLD);
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_schema_tag() OWNER TO postgres;
-- ddl-end --

-- object: after_insert_schema_tag | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_schema_tag ON public.resource_tag CASCADE;
CREATE TRIGGER after_insert_schema_tag
	AFTER INSERT 
	ON public.resource_tag
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_schema_tag();
-- ddl-end --

-- object: after_delete_schema_tag | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_schema_tag ON public.resource_tag CASCADE;
CREATE TRIGGER after_delete_schema_tag
	AFTER DELETE 
	ON public.resource_tag
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_schema_tag();
-- ddl-end --

-- object: public.after_insert_ace | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_ace() CASCADE;
CREATE FUNCTION public.after_insert_ace ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
    v_count_tenant integer;
BEGIN
	

	IF NEW.source_id IS NULL THEN
	    -- insert a new row for each sub element (of tenant tree)
	 	-- source_id is the ID of the inserted row
		INSERT INTO ace(role_id, identity_id, resource_id, source_id)
		SELECT NEW.role_id, NEW.identity_id, descendant_id, NEW.id
		FROM resource_tenant_tree WHERE ancestor_id = NEW.resource_id AND depth > 0;

		-- set effective ACE

		DROP TABLE IF EXISTS temp_after_insert_ace;
	    CREATE TEMP TABLE temp_after_insert_ace AS
	        SELECT a.*
	        FROM ace a
	        WHERE id = NEW.id OR source_id = NEW.id;
		PERFORM sync_resource_aces('temp_after_insert_ace'::regclass);

	END IF;

	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_ace() OWNER TO postgres;
-- ddl-end --

-- object: after_insert_ace | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_ace ON public.ace CASCADE;
CREATE TRIGGER after_insert_ace
	AFTER INSERT 
	ON public.ace
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_ace();
-- ddl-end --

-- object: public.after_insert_field_tag | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_field_tag() CASCADE;
CREATE FUNCTION public.after_insert_field_tag ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	-- Adding a tag on a field is adding a restriction. 
	-- We need to remove all role_id that are now denied usage of the field
	DELETE FROM field_ace fa
	WHERE fa.field_id = NEW.field_id AND
				fa.role_id NOT IN (
					SELECT rt.role_id
					FROM role_tag rt, field_tag ft, resource t
					WHERE ft.field_id = NEW.field_id AND
								ft.tag_id = NEW.tag_id AND
								ft.tag_id = t.id AND
								t.label LIKE REPLACE(rt.tag, '*', '%')
				);


	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_field_tag() OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_resource | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_resource() CASCADE;
CREATE FUNCTION public.after_insert_resource ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	INSERT INTO resource_tenant_tree(ancestor_id,descendant_id,depth)
	SELECT ancestor_id, NEW.id, depth + 1
	FROM resource_tenant_tree WHERE descendant_id = NEW.tenant_id
	UNION ALL SELECT NEW.id, NEW.id, 0;

    --------------------------------------------
    -- Insert all  inherited ACE
    --------------------------------------------   
     
    INSERT INTO ace (identity_id, resource_id, role_id, source_id) 
    SELECT a.identity_id, NEW.id, a.role_id, a.id 
	FROM ace a
    WHERE a.source_id is NULL AND 
         	    a.resource_id IN (
	            SELECT rtt.ancestor_id FROM resource_tenant_tree rtt
	            WHERE rtt.descendant_id = NEW.id
	                AND rtt.depth > 0
        );  

	DROP TABLE IF EXISTS temp_after_insert_resource;
    CREATE TEMP TABLE temp_after_insert_resource AS
        SELECT a.*
        FROM ace a
        WHERE resource_id = NEW.id;
	PERFORM sync_resource_aces('temp_after_insert_resource'::regclass);

    --------------------------------------------
    -- Manage public resources
    -- we create a non source ACE with a specific role and the tenant as identity
    -------------------------------------------- 
 	IF NEW.pub IS TRUE THEN
		PERFORM insert_pub_ace(NEW);
	END IF;

    --------------------------------------------
    -- Manage resource type
    -------------------------------------------- 
    UPDATE resource SET "type" = (
	  SELECT (
		CASE 
		  WHEN s.label LIKE 'service.%' THEN 1 
		  WHEN s.label LIKE 'tenant.%' THEN 2 
		  WHEN s.label LIKE 'provider.%' THEN 3 
		  WHEN s.label LIKE 'blueprint.%' THEN 5 
		  WHEN s.label = 'schema' THEN 6 
          WHEN s.label LIKE 'request.%' THEN 10 
		  ELSE 4 
		END	
	  ) 
	  FROM resource r, resource s
	  WHERE r.id = NEW.id AND r.schema_id = s.id
    ) WHERE id = NEW.id;

    --------------------------------------------
    -- Add entry in audit log
    --------------------------------------------
	-- INSERT INTO audit_log(resource_id, "after", "user", "created_at", "action")
    -- SELECT NEW.id, NEW.content ||  jsonb_build_object('label', NEW.label , 'schema', NEW.schema_id, 'tenant', NEW.tenant_id, 'public', NEW.pub, 'link', NEW.link_id), NEW.created_by,NEW.created_at, 'create'; 

    PERFORM pg_notify('resource_channel', 'create,' || NEW.id || ',' || NEW.tenant_id || ',' || NEW.schema_id || ',' || NEW.label);

	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_resource() OWNER TO postgres;
-- ddl-end --

-- object: public.after_update_resource | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_update_resource() CASCADE;
CREATE FUNCTION public.after_update_resource ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	IF NEW.tenant_id <> OLD.tenant_id OR (NEW.tenant_id is NULL and OLD.tenant_id is NOT NULL)  OR (NEW.tenant_id is NOT NULL and OLD.tenant_id is  NULL)  THEN
		--------------------------------------------
		-- Step 1: Disconnect from current ancestors
		-- Delete all paths that end at descendants in the subtree
		--------------------------------------------
		DELETE FROM resource_tenant_tree
		WHERE descendant_id IN (SELECT descendant_id FROM resource_tenant_tree WHERE ancestor_id = OLD.id)
		AND ancestor_id NOT IN (SELECT descendant_id FROM resource_tenant_tree WHERE ancestor_id = OLD.id);
		
		--------------------------------------------
		-- Step 2: Mount subtree to new ancestors
		-- Insert rows matching ancestors of insertion point and descendants of subtree
		--------------------------------------------
		INSERT INTO resource_tenant_tree (ancestor_id, descendant_id, depth)
		SELECT DISTINCT supertree.ancestor_id, subtree.descendant_id, supertree.depth + subtree.depth + 1
		FROM resource_tenant_tree AS supertree
		CROSS JOIN resource_tenant_tree AS subtree
		WHERE subtree.ancestor_id = OLD.id
		AND supertree.descendant_id = NEW.tenant_id;
        
		--------------------------------------------
		-- Step 3: Remove all inherited ACE
		--------------------------------------------
        DELETE FROM ace
        WHERE resource_id = OLD.id AND source_id is not NULL;

		--------------------------------------------
		-- Step 4: Insert all new inherited ACE
		--------------------------------------------        
        INSERT INTO ace (identity_id, resource_id, role_id, source_id)
        SELECT DISTINCT a.identity_id, NEW.id, a.role_id, a.id FROM ace a
        WHERE a.source_id is NULL 
            AND a.resource_id IN (
                SELECT rtt.ancestor_id FROM resource_tenant_tree rtt
                WHERE rtt.descendant_id = NEW.tenant_id
            );

		--------------------------------------------
		-- Step 4: Manage pub
		--------------------------------------------   
		IF OLD.pub IS TRUE AND NEW.pub IS TRUE THEN
			-- remove OLD pub ACE
			PERFORM remove_pub_ace(OLD);
			-- add NEW pub ACE
			PERFORM insert_pub_ace(NEW);
		END IF;

		--------------------------------------------
		-- Step 5: Sync resource ACE for this resource
		-------------------------------------------- 
		DROP TABLE IF EXISTS temp_after_insert_ace;
	    CREATE TEMP TABLE temp_after_insert_ace AS
	        SELECT a.*
	        FROM ace a
	        WHERE resource_id = NEW.id;
		PERFORM sync_resource_aces('temp_after_insert_ace'::regclass);


    END IF;

	IF NEW.pub <> OLD.pub THEN
		IF NEW.pub IS TRUE THEN
		    PERFORM insert_pub_ace(NEW);
		ELSE
			PERFORM remove_pub_ace(OLD);
		END IF;
	END IF;

    --------------------------------------------
    -- Add entry in audit log
    --------------------------------------------
	-- INSERT INTO audit_log(resource_id, "before", "after", "user", "created_at", "action")
    -- SELECT 
	-- 	NEW.id, 
    --     OLD.content ||  jsonb_build_object('label', OLD.label , 'schema', OLD.schema_id, 'tenant', OLD.tenant_id, 'public', OLD.pub, 'link', OLD.link_id), 
	-- 	NEW.content ||  jsonb_build_object('label', NEW.label , 'schema', NEW.schema_id, 'tenant', NEW.tenant_id, 'public', NEW.pub, 'link', NEW.link_id), 
	-- 	NEW.updated_by,
	-- 	NEW.updated_at, 
	-- 	'update'; 


    PERFORM pg_notify('resource_channel', 'update,' || NEW.id || ',' || NEW.tenant_id || ',' || NEW.schema_id || ',' || NEW.label);

	RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_update_resource() OWNER TO postgres;
-- ddl-end --

-- object: public.after_update_schema_label | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_update_schema_label() CASCADE;
CREATE FUNCTION public.after_update_schema_label ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
	v_count integer;
BEGIN

	
	SELECT COUNT(*) INTO v_count FROM "schema" WHERE id = NEW.id;
	
	IF v_count > 0 THEN
		-- get all role having acces to the old schema
		DROP TABLE IF EXISTS role_schema_old_temp;
		CREATE TEMP TABLE role_schema_old_temp AS
			SELECT role_id
			FROM role_schema
			WHERE OLD.label LIKE REPLACE("schema", '*', '%');

		UPDATE role_schema SET "schema" = NEW.label
		WHERE "schema" = OLD.label;

		-- get all role having acces to the new schema
		DROP TABLE IF EXISTS role_schema_new_temp;
		CREATE TEMP TABLE role_schema_new_temp AS
			SELECT role_id
			FROM role_schema
			WHERE NEW.label LIKE REPLACE("schema", '*', '%');

		-- get all role having access to the new schema but not the old schema
		-- we need to check all ace related to these role and check if we need to add a resource_ace
		PERFORM sync_resource_ace(ace)
		FROM ace
		WHERE ace.role_id IN (
			SELECT rsnt.role_id
			FROM role_schema_new_temp rsnt
					  LEFT OUTER JOIN role_schema_old_temp rsot ON rsot.role_id = rsnt.role_id
			WHERE rsot.role_id IS NULL
		);

		-- get all role having access to the old schema but not the new schema
		-- we remove all resource_ace whose resource_id if of type "NEW.label" and role_id is one of the role ID present is old table and not in new table
		DELETE FROM resource_ace
		WHERE ace_id IN (
			SELECT ra.ace_id
			FROM resource_ace ra, resource r, resource s
			WHERE ra.resource_id = r.id AND
                  r.schema_id = s.id AND
                  s."label" = NEW.label AND
                  ra.role_id IN (
                      SELECT rsot.role_id
                      FROM role_schema_old_temp rsot
                                LEFT OUTER JOIN role_schema_new_temp rsnt ON rsot.role_id = rsnt.role_id
                      WHERE rsnt.role_id IS NULL 
                  )
		);
	
	END IF;
	
RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_update_schema_label() OWNER TO postgres;
-- ddl-end --

-- object: public.after_update_link_source_label | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_update_link_source_label() CASCADE;
CREATE FUNCTION public.after_update_link_source_label ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	UPDATE resource SET label = NEW.label, schema_id = NEW.schema_id  WHERE link_id = NEW.id;
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_update_link_source_label() OWNER TO postgres;
-- ddl-end --

-- object: public.effective_ace | type: TYPE --
-- DROP TYPE IF EXISTS public.effective_ace CASCADE;
CREATE TYPE public.effective_ace AS
(
 id integer,
 identity_id integer,
 resource_id integer,
 role_id integer,
 source_id integer,
 is_effective boolean
);
-- ddl-end --
ALTER TYPE public.effective_ace OWNER TO postgres;
-- ddl-end --

-- object: public.is_effective_ace | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.is_effective_ace(regclass) CASCADE;
CREATE FUNCTION public.is_effective_ace (v_table_ace regclass)
	RETURNS SETOF public.effective_ace
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	ROWS 1000
	AS $$
BEGIN
    -- we need to calculate effective resource ace (check if the identity has effective access on the object)
    -- a identity is granted access if all following conditions are met
    --     - the role has access to the resource schema (table schema role) or to all schema
    --     - if the schema is protected by security tag(s), the role must have access to at least one of these security tags
    --     - if the resource is protected by security tag(s), the role must have access to at least one of these security tags

    DROP TABLE IF EXISTS ace_effective_temp;
	CREATE TEMP TABLE ace_effective_temp OF effective_ace (PRIMARY KEY(id));
	EXECUTE 'INSERT INTO ace_effective_temp SELECT id, identity_id, resource_id, role_id, source_id, FALSE FROM ' || v_table_ace;

    -- check if security tags match for schema
    DROP TABLE IF EXISTS schema_tag_temp;
    CREATE TEMP TABLE schema_tag_temp AS
        SELECT a.id as ace_id, t."label" as schema_tag_label
        FROM ace_effective_temp a,
				  resource r LEFT OUTER JOIN resource_tag st JOIN resource t ON st.tag_id = t.id
								  ON r.schema_id = st.resource_id
        WHERE r.id = a.resource_id;
    
    DROP TABLE IF EXISTS resource_tag_temp;
    CREATE TEMP TABLE resource_tag_temp AS
        SELECT a.id as ace_id, t."label" as resource_tag_label
        FROM ace_effective_temp a, 
				  resource r LEFT OUTER JOIN resource_tag rt JOIN resource t ON rt.tag_id = t.id
								  ON r.id = rt.resource_id
        WHERE r.id = a.resource_id;

    DROP TABLE IF EXISTS role_tag_temp;
    CREATE TEMP TABLE role_tag_temp AS
        SELECT a.id as ace_id, rt."tag" AS role_allowed_tag_label
        FROM ace_effective_temp a, resource r, role_tag rt
        WHERE rt.role_id = r.id
            AND rt.role_id = a.role_id;

	DROP TABLE IF EXISTS temp_result;
	CREATE TEMP TABLE temp_result AS
	SELECT a.id
	FROM  resource r, role_schema sr, resource s,
			   ace_effective_temp a LEFT OUTER JOIN role_tag_temp rott ON a.id = rott.ace_id
					    LEFT OUTER JOIN schema_tag_temp stt ON a.id = stt.ace_id
					    LEFT OUTER JOIN resource_tag_temp rett ON a.id = rett.ace_id
	WHERE sr.role_id = a.role_id AND
				r.id = a.resource_id AND 
        		r.schema_id = s.id AND
				s.label LIKE REPLACE(sr.schema, '*', '%') AND
	          (
	           (stt.schema_tag_label IS NULL AND rett.resource_tag_label IS NULL) OR
	           (stt.schema_tag_label IS NULL AND rett.resource_tag_label LIKE REPLACE(rott.role_allowed_tag_label, '*', '%')) OR
	           (rett.resource_tag_label IS NULL AND stt.schema_tag_label LIKE REPLACE(rott.role_allowed_tag_label, '*', '%')) OR
	           (stt.schema_tag_label LIKE REPLACE(rott.role_allowed_tag_label, '*', '%') AND rett.resource_tag_label LIKE REPLACE(rott.role_allowed_tag_label, '*', '%'))
	          );

    UPDATE ace_effective_temp a 
	SET is_effective = TRUE
	FROM temp_result tr
	WHERE tr.id = a.id;

    RETURN QUERY SELECT * FROM ace_effective_temp;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.is_effective_ace(regclass) OWNER TO postgres;
-- ddl-end --

-- object: public.sync_resource_aces | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.sync_resource_aces(regclass) CASCADE;
CREATE FUNCTION public.sync_resource_aces (v_table_ace regclass)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
    DROP TABLE IF EXISTS resource_ace_effective_temp;
	CREATE TEMP TABLE resource_ace_effective_temp OF effective_ace (PRIMARY KEY(id));
	INSERT INTO resource_ace_effective_temp
	SELECT * FROM is_effective_ace(v_table_ace::regclass);
	

    INSERT INTO resource_ace(identity_id, resource_id, role_id, ace_id)
    SELECT identity_id, resource_id, role_id, id
	FROM resource_ace_effective_temp
	WHERE is_effective IS TRUE
    ON CONFLICT ON CONSTRAINT resource_ace_pk DO NOTHING;

	DELETE FROM resource_ace 
	WHERE ace_id IN (
		SELECT id FROM resource_ace_effective_temp	
		WHERE is_effective IS FALSE
	);
END;
$$;
-- ddl-end --
ALTER FUNCTION public.sync_resource_aces(regclass) OWNER TO postgres;
-- ddl-end --

-- object: public.sync_resource_ace | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.sync_resource_ace(public.ace) CASCADE;
CREATE FUNCTION public.sync_resource_ace (v_ace public.ace)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
    DROP TABLE IF EXISTS ace_single_temp;
	CREATE TEMP TABLE ace_single_temp (id integer PRIMARY KEY, identity_id integer, resource_id integer, role_id integer, source_id integer);
	INSERT INTO ace_single_temp VALUES (v_ace.id, v_ace.identity_id, v_ace.resource_id, v_ace.role_id, v_ace.source_id);
	
	PERFORM sync_resource_aces('ace_single_temp'::regclass);

END;
$$;
-- ddl-end --
ALTER FUNCTION public.sync_resource_ace(public.ace) OWNER TO postgres;
-- ddl-end --

-- object: public.is_unique | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.is_unique(text,integer,integer,text) CASCADE;
CREATE FUNCTION public.is_unique (v_label text, v_tenant_id integer, v_schema_id integer, v_tenant_scope text)
	RETURNS integer
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
    v_tenant_scope_id integer;
    v_id integer;
BEGIN
	SELECT a.id INTO v_tenant_scope_id
	FROM resource a, resource s, resource_tenant_tree rtt
	WHERE 
		rtt.descendant_id = v_tenant_id 
		AND rtt.ancestor_id = a.id
		AND a.schema_id = s.id
		AND s.label = v_tenant_scope
	ORDER BY rtt.depth DESC
	LIMIT 1;

	IF v_tenant_scope_id IS NULL THEN
		v_tenant_scope_id := v_tenant_id;
	END IF;

	WITH tenant_tree AS (
		SELECT td.id as id
		FROM resource t 
			JOIN resource_tenant_tree rttd 
				JOIN resource td
					JOIN resource sd ON td.schema_id = sd.id AND sd.label LIKE 'tenant.%'
				ON td.id = rttd.descendant_id
			ON rttd.ancestor_id = t.id
		WHERE t.id = v_tenant_scope_id		
	), schema_tree AS (
		SELECT v_schema_id as id
		UNION
		SELECT sia.ancestor_id as id
		FROM resource s 
				JOIN schema_inheritance sia
				ON sia.descendant_id = s.id AND sia.depth > 0
		WHERE s.id = v_schema_id
		UNION
		SELECT sid.descendant_id as id
		FROM resource s 
				JOIN schema_inheritance sid
				ON sid.ancestor_id = s.id AND sid.depth > 0
		WHERE s.id = v_schema_id
	)
	SELECT DISTINCT r.id into v_id
	FROM resource r
			JOIN schema_inheritance si
				JOIN schema_tree st ON st.id = si.descendant_id OR st.id = si.ancestor_id
			ON r.schema_id = si.descendant_id OR r.schema_id = si.ancestor_id
			JOIN resource_tenant_tree rtt 
				JOIN tenant_tree tt ON tt.id = rtt.descendant_id OR tt.id = rtt.ancestor_id
			ON r.tenant_id = rtt.descendant_id OR r.tenant_id = rtt.ancestor_id
	WHERE   r.label = v_label
	LIMIT 1;

	RETURN v_id;

	
END;
$$;
-- ddl-end --
ALTER FUNCTION public.is_unique(text,integer,integer,text) OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_schema | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_schema() CASCADE;
CREATE FUNCTION public.after_delete_schema ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	DELETE FROM schema_inheritance
	    WHERE ancestor_id  =OLD.id;
	DELETE FROM schema_inheritance
	    WHERE descendant_id  =OLD.id;
	RETURN NULL;
END;

$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_schema() OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_schema | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_schema() CASCADE;
CREATE FUNCTION public.after_insert_schema ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	INSERT INTO schema_inheritance(ancestor_id,descendant_id,depth) VALUES(NEW.id, NEW.id, 0);
	INSERT INTO schema_parent(schema_id, parent_id, "order") 
		select r.id, (s #>> '{id}')::integer, ROW_NUMBER() OVER()
		from resource r join json_array_elements((r.content->'parents')::json) s on true
		where r.id = NEW.id;
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_schema() OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_schema_parent | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_schema_parent() CASCADE;
CREATE FUNCTION public.after_insert_schema_parent ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	-- create the child-parent relation and propagate to all descendant of schema_id
	INSERT INTO schema_inheritance(ancestor_id,descendant_id,depth, schema_parent_id)
	SELECT NEW.parent_id, descendant_id, depth + 1, NEW.id
	FROM schema_inheritance 
	WHERE ancestor_id = NEW.schema_id;

	-- insert all it's new parent ancestors as own ancestors (with a depth of +1) and do the same for it's descendant
	INSERT INTO schema_inheritance(ancestor_id,descendant_id,depth, schema_parent_id)
	SELECT sia.ancestor_id, sid.descendant_id, sia.depth + sid.depth + 1, sia.schema_parent_id
	FROM schema_inheritance sia, schema_inheritance sid
	WHERE sia.descendant_id = NEW.parent_id AND sia.depth > 0 AND sid.ancestor_id = NEW.schema_id;

	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_schema_parent() OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_schema_parent | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_schema_parent() CASCADE;
CREATE FUNCTION public.after_delete_schema_parent ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	-- delete all entries
	DELETE FROM schema_inheritance WHERE schema_parent_id  =OLD.id;
	RETURN NULL;
END;

$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_schema_parent() OWNER TO postgres;
-- ddl-end --

-- object: public.schema_parent_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.schema_parent_id_seq CASCADE;
CREATE SEQUENCE public.schema_parent_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.schema_parent_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.resource_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.resource_id_seq CASCADE;
CREATE SEQUENCE public.resource_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.resource_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.schema_parent | type: TABLE --
-- DROP TABLE IF EXISTS public.schema_parent CASCADE;
CREATE TABLE public.schema_parent (
	id integer NOT NULL DEFAULT nextval('public.schema_parent_id_seq'::regclass),
	schema_id integer,
	parent_id integer,
	"order" smallint DEFAULT 1,
	CONSTRAINT schema_parent_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.schema_parent OWNER TO postgres;
-- ddl-end --

-- object: schema_parent_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.schema_parent_idx CASCADE;
CREATE INDEX schema_parent_idx ON public.schema_parent
USING btree
(
	schema_id,
	parent_id ASC NULLS LAST
);
-- ddl-end --

-- object: public.resource | type: TABLE --
-- DROP TABLE IF EXISTS public.resource CASCADE;
CREATE TABLE public.resource (
	id integer NOT NULL DEFAULT nextval('public.resource_id_seq'::regclass),
	urn varchar NOT NULL,
	label varchar(255) NOT NULL,
	pub bool DEFAULT false,
	tenant_id integer,
	link_id integer,
	link_type smallint DEFAULT 0,
	schema_id integer,
	type smallint DEFAULT 4,
	content jsonb,
	created_at timestamp with time zone DEFAULT now(),
	updated_at timestamp with time zone,
	created_by varchar(255),
	updated_by varchar(255),
	CONSTRAINT resource_pk PRIMARY KEY (id),
	CONSTRAINT resource_urn_unique UNIQUE (urn)
);
-- ddl-end --
COMMENT ON COLUMN public.resource.urn IS E'The ID visible externally. The format of the URL is: urn:<tenant>:<schema>:<label>';
-- ddl-end --
COMMENT ON COLUMN public.resource.link_type IS E'0=normal,1=ref';
-- ddl-end --
ALTER TABLE public.resource OWNER TO postgres;
-- ddl-end --

-- object: resource_label_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_label_idx CASCADE;
CREATE INDEX resource_label_idx ON public.resource
USING btree
(
	label
);
-- ddl-end --

-- object: after_insert_schema_parent | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_schema_parent ON public.schema_parent CASCADE;
CREATE TRIGGER after_insert_schema_parent
	AFTER INSERT 
	ON public.schema_parent
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_schema_parent();
-- ddl-end --

-- object: after_delete_schema_parent | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_schema_parent ON public.schema_parent CASCADE;
CREATE TRIGGER after_delete_schema_parent
	AFTER DELETE 
	ON public.schema_parent
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_schema_parent();
-- ddl-end --

-- object: resource_tenant_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_tenant_idx CASCADE;
CREATE INDEX resource_tenant_idx ON public.resource
USING btree
(
	tenant_id
);
-- ddl-end --

-- object: after_insert_resource | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_resource ON public.resource CASCADE;
CREATE TRIGGER after_insert_resource
	AFTER INSERT 
	ON public.resource
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_resource();
-- ddl-end --

-- object: resource_link_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_link_idx CASCADE;
CREATE INDEX resource_link_idx ON public.resource
USING btree
(
	link_id
);
-- ddl-end --

-- object: after_update_resource | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_update_resource ON public.resource CASCADE;
CREATE TRIGGER after_update_resource
	AFTER UPDATE
	ON public.resource
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_update_resource();
-- ddl-end --

-- object: resource_schema_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_schema_idx CASCADE;
CREATE INDEX resource_schema_idx ON public.resource
USING btree
(
	schema_id
);
-- ddl-end --

-- object: after_update_schema_label | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_update_schema_label ON public.resource CASCADE;
CREATE TRIGGER after_update_schema_label
	AFTER UPDATE
	ON public.resource
	FOR EACH ROW
	WHEN (((new.label)::text <> (old.label)::text))
	EXECUTE PROCEDURE public.after_update_schema_label();
-- ddl-end --

-- object: after_delete_resource | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_resource ON public.resource CASCADE;
CREATE TRIGGER after_delete_resource
	AFTER DELETE 
	ON public.resource
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_resource();
-- ddl-end --

-- object: after_update_link_source_label | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_update_link_source_label ON public.resource CASCADE;
CREATE TRIGGER after_update_link_source_label
	AFTER UPDATE
	ON public.resource
	FOR EACH ROW
	WHEN ((((new.label)::text <> (old.label)::text)
	or (new.schema_id <> old.schema_id)))
	EXECUTE PROCEDURE public.after_update_link_source_label();
-- ddl-end --

-- object: public.insert_pub_ace | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.insert_pub_ace(public.resource) CASCADE;
CREATE FUNCTION public.insert_pub_ace (v_resource public.resource)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN

    INSERT INTO ace (identity_id, resource_id, role_id)
    SELECT v_resource.tenant_id, v_resource.id, r.id
	FROM resource r, resource s
    WHERE r.schema_id = s.id AND
				s.label = 'iam.role' AND
				r.label = 'Public';

END;
$$;
-- ddl-end --
ALTER FUNCTION public.insert_pub_ace(public.resource) OWNER TO postgres;
-- ddl-end --

-- object: public.remove_pub_ace | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.remove_pub_ace(public.resource) CASCADE;
CREATE FUNCTION public.remove_pub_ace (v_resource public.resource)
	RETURNS void
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN

	DELETE FROM ace
	WHERE identity_id = v_resource.tenant_id AND
				resource_id = v_resource.id AND
				role_id IN (
				    SELECT r.id
					FROM resource r, resource s
				    WHERE r.schema_id = s.id AND
								s.label = 'security.role' AND
								r.label = 'Public'
				);

END;
$$;
-- ddl-end --
ALTER FUNCTION public.remove_pub_ace(public.resource) OWNER TO postgres;
-- ddl-end --

-- object: resource_content_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_content_idx CASCADE;
CREATE INDEX resource_content_idx ON public.resource
USING gin
(
	content
);
-- ddl-end --

-- object: public.audit_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.audit_id_seq CASCADE;
CREATE SEQUENCE public.audit_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.audit_id_seq OWNER TO postgres;
-- ddl-end --

-- object: ace_exist_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.ace_exist_idx CASCADE;
CREATE INDEX ace_exist_idx ON public.ace
USING btree
(
	resource_id,
	identity_id ASC NULLS LAST,
	role_id ASC NULLS LAST
);
-- ddl-end --

-- object: public.after_update_resource_label | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_update_resource_label() CASCADE;
CREATE FUNCTION public.after_update_resource_label ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
    record RECORD;
BEGIN
  -- get all fields whose type is the schema (or inherited schema) of the deleted resource
  DROP TABLE IF EXISTS temp_resource_fields;
  CREATE TEMPORARY TABLE temp_resource_fields AS
  SELECT fs.schema_id as schema_id, 
         f.label as field_label, 
         f.multiple as multiple
  FROM   resource s, 
         schema_inheritance si, 
         field f,
         field_schema fs
  WHERE  si.descendant_id= OLD.schema_id AND 
         s.id = si.ancestor_id  AND
         f.id = fs.field_id AND
         f.type = s.label;

  -- rename many relationship
    FOR record IN
        SELECT * FROM temp_resource_fields
    LOOP  
        UPDATE resource r
        SET    content =jsonb_set(content, ('{' ||r2.field_label  || ',' || r2.pos || ',@l}')::text[],to_jsonb(NEW.label))
        FROM (
          SELECT   r.id, ordinality - 1 AS pos, record.field_label as field_label
          FROM     resource r, jsonb_array_elements(r.content->record.field_label) with ordinality
          WHERE r.schema_id = record.schema_id
                AND (value->>'@l')::text = OLD.label
                AND record.multiple is TRUE
          ) r2
        WHERE r2.id = r.id;
  END LOOP;

  -- rename one relationship
    FOR record IN
        SELECT * FROM temp_resource_fields
    LOOP  
        UPDATE resource r
        SET    content = jsonb_set(r.content, ('{' || record.field_label  || ',@l}')::text[], to_jsonb(NEW.label))
        FROM (
          SELECT   r.id
          FROM     resource r, jsonb_each(r.content) fields
          WHERE r.schema_id = record.schema_id
			    AND fields.key = record.field_label
                AND (fields.value->>'@l')::text = OLD.label
                AND record.multiple is NOT TRUE
          ) r2
        WHERE r2.id = r.id;           
    END LOOP;

  RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_update_resource_label() OWNER TO postgres;
-- ddl-end --

-- object: after_update_resource_label | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_update_resource_label ON public.resource CASCADE;
CREATE TRIGGER after_update_resource_label
	AFTER UPDATE
	ON public.resource
	FOR EACH ROW
	WHEN (((old.label)::text <> (new.label)::text))
	EXECUTE PROCEDURE public.after_update_resource_label();
-- ddl-end --

-- object: public.audit_log_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.audit_log_id_seq CASCADE;
CREATE SEQUENCE public.audit_log_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.audit_log_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.after_insert_group_membership | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_insert_group_membership() CASCADE;
CREATE FUNCTION public.after_insert_group_membership ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
        UPDATE resource r
        SET content = jsonb_set(r.content, ('{member_of}')::text[], r2.member_of), updated_by = g.updated_by, updated_at = g.updated_at
        FROM (
          select c.id as id, json_agg(json_build_object('id', pr.id, 'schema', prs.label, 'label', pr.label))::jsonb as member_of
          from group_membership gm, resource p, resource pr, resource prs, resource c, resource cr, resource crs
          WHERE gm.parent_id = p.id AND
              gm.child_id = c.id AND
              p.label = pr.label AND
              pr.schema_id = prs.id AND
              prs.label = 'iam.identity.group_ref' AND
              c.label = cr.label AND
              cr.schema_id = crs.id AND
              crs.label IN ('iam.identity.group_ref', 'iam.identity.user_ref') AND
              c.id = NEW.child_id
          group by c.id
        ) r2, resource g
        WHERE r2.id = r.id and g.id = new.parent_id;
  RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_insert_group_membership() OWNER TO postgres;
-- ddl-end --

-- object: public.after_delete_group_membership | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_delete_group_membership() CASCADE;
CREATE FUNCTION public.after_delete_group_membership ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
        UPDATE resource r
        SET content = jsonb_set(r.content, ('{member_of}')::text[], r2.member_of), updated_by = g.updated_by, updated_at = g.updated_at
        FROM (
          select c.id as id, json_agg(json_build_object('id', pr.id, 'schema', prs.label, 'label', pr.label))::jsonb as member_of
          from group_membership gm, resource p, resource pr, resource prs, resource c, resource cr, resource crs
          WHERE gm.parent_id = p.id AND
              gm.child_id = c.id AND
              p.label = pr.label AND
              pr.schema_id = prs.id AND
              prs.label = 'iam.identity.group_ref' AND
              c.label = cr.label AND
              cr.schema_id = crs.id AND
              crs.label IN ('iam.identity.group_ref', 'iam.identity.user_ref') AND
              c.id = OLD.child_id
          group by c.id
        ) r2, resource g
        WHERE r2.id = r.id and g.id = OLD.parent_id;            
  RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_delete_group_membership() OWNER TO postgres;
-- ddl-end --

-- object: after_insert_group_membership | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_group_membership ON public.group_membership CASCADE;
CREATE TRIGGER after_insert_group_membership
	AFTER INSERT 
	ON public.group_membership
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_insert_group_membership();
-- ddl-end --

-- object: after_delete_group_membership | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_group_membership ON public.group_membership CASCADE;
CREATE TRIGGER after_delete_group_membership
	AFTER DELETE 
	ON public.group_membership
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_delete_group_membership();
-- ddl-end --

-- object: public.acquire_lock | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.acquire_lock(character varying,integer) CASCADE;
CREATE FUNCTION public.acquire_lock (name character varying, duration integer DEFAULT 3600)
	RETURNS boolean
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE 
  res boolean;
  ids text[];
  lock_expired_at timestamp;
  i text;
BEGIN
  /* Init result */
  res = false;
 
  SELECT string_to_array(name, ',') INTO ids;
 
  FOREACH i IN ARRAY ids LOOP
    /* Try to acquire the application lock */
    SELECT expired_at INTO lock_expired_at FROM locks WHERE id = hashtext(i);
    IF lock_expired_at is NULL THEN  /* first lock or the previous unlock was successful */
      INSERT INTO locks (id, expired_at) VALUES (hashtext(i), NOW()::timestamp + (duration || ' seconds')::INTERVAL);
      res = true;
    ELSIF lock_expired_at < NOW()::timestamp then  /* the previous lock is still present but we hit the timeout -> acquire the lock*/
      delete from locks where id = hashtext(i);
      INSERT INTO locks (id, expired_at) VALUES (hashtext(i), NOW()::timestamp + (duration || ' seconds')::INTERVAL);
      res = true;
    else
      RAISE 'Cannot acquire lock';
    END IF;
  END LOOP;
  RETURN res;
EXCEPTION WHEN OTHERS then
	return false;
END
$$;
-- ddl-end --
ALTER FUNCTION public.acquire_lock(character varying,integer) OWNER TO postgres;
-- ddl-end --

-- object: public.locks | type: TABLE --
-- DROP TABLE IF EXISTS public.locks CASCADE;
CREATE TABLE public.locks (
	id integer NOT NULL,
	expired_at timestamp,
	CONSTRAINT locks_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.locks OWNER TO postgres;
-- ddl-end --

-- object: public.drop_lock | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.drop_lock(character varying) CASCADE;
CREATE FUNCTION public.drop_lock (name character varying)
	RETURNS boolean
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
  res boolean;
  lock_id integer;
  ids text[];
  i text;
BEGIN
  /* Init result */
  res = false;
 
  SELECT string_to_array(name, ',') INTO ids;
 
  FOREACH i IN ARRAY ids LOOP

    /* Try to acquire the application lock */
    SELECT id INTO lock_id FROM locks WHERE id = hashtext(i);
  
    IF lock_id is NOT NULL THEN
      DELETE FROM locks WHERE id = hashtext(i);
      res = true;
    END IF;
  END LOOP;

  RETURN res;
END

$$;
-- ddl-end --
ALTER FUNCTION public.drop_lock(character varying) OWNER TO postgres;
-- ddl-end --

-- object: public.catalog_item_id_seq | type: SEQUENCE --
-- DROP SEQUENCE IF EXISTS public.catalog_item_id_seq CASCADE;
CREATE SEQUENCE public.catalog_item_id_seq
	INCREMENT BY 1
	MINVALUE 0
	MAXVALUE 2147483647
	START WITH 1
	CACHE 1
	NO CYCLE
	OWNED BY NONE;

-- ddl-end --
ALTER SEQUENCE public.catalog_item_id_seq OWNER TO postgres;
-- ddl-end --

-- object: public.role_hierarchy | type: TABLE --
-- DROP TABLE IF EXISTS public.role_hierarchy CASCADE;
CREATE TABLE public.role_hierarchy (
	parent_id integer NOT NULL,
	child_id integer NOT NULL,
	CONSTRAINT role_hierarchy_pk PRIMARY KEY (parent_id,child_id)
);
-- ddl-end --
COMMENT ON TABLE public.role_hierarchy IS E'Track if a role is contained in another role\nA role contains another role if he have at least\n- access to all schemas of the inner role\n- access to all actions of the inner role\n- access to all fields of the inner role\n- access to all security tags of the inner role';
-- ddl-end --
ALTER TABLE public.role_hierarchy OWNER TO postgres;
-- ddl-end --

-- object: resource_type_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_type_idx CASCADE;
CREATE INDEX resource_type_idx ON public.resource
USING btree
(
	type,
	label
);
-- ddl-end --

-- object: pg_trgm | type: EXTENSION --
-- DROP EXTENSION IF EXISTS pg_trgm CASCADE;
CREATE EXTENSION pg_trgm
WITH SCHEMA public;
-- ddl-end --

-- object: resource_label_gin_index | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_label_gin_index CASCADE;
CREATE INDEX resource_label_gin_index ON public.resource
USING gin
(
	label gin_trgm_ops
);
-- ddl-end --

-- object: resource_public_id_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.resource_public_id_idx CASCADE;
CREATE UNIQUE INDEX resource_public_id_idx ON public.resource
USING btree
(
	urn
)
INCLUDE (urn);
-- ddl-end --

-- object: after_insert_schema | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_schema ON public.resource CASCADE;
CREATE TRIGGER after_insert_schema
	AFTER INSERT 
	ON public.resource
	FOR EACH ROW
	WHEN ((new.schema_id = 1))
	EXECUTE PROCEDURE public.after_insert_schema();
-- ddl-end --

-- object: after_delete_schema | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_schema ON public.resource CASCADE;
CREATE TRIGGER after_delete_schema
	AFTER DELETE 
	ON public.resource
	FOR EACH ROW
	WHEN ((old.schema_id = 1))
	EXECUTE PROCEDURE public.after_delete_schema();
-- ddl-end --

-- object: public.after_add_or_delete_role | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_add_or_delete_role() CASCADE;
CREATE FUNCTION public.after_add_or_delete_role ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	IF OLD.id IS NOT NULL AND OLD.schema_id = 5 THEN
		delete from role_schema 
		where role_id = OLD.id;
	END IF;

    IF NEW.id IS NOT NULL AND NEW.schema_id = 5 THEN
		insert into role_schema 
		select r.id, s #>> '{}'
		from resource r join json_array_elements((r.content->'schemas')::json) s on true
		where r.id = NEW.id;
 	END IF;
	
	RETURN NULL;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_add_or_delete_role() OWNER TO postgres;
-- ddl-end --

-- object: after_add_or_delete_role | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_add_or_delete_role ON public.resource CASCADE;
CREATE TRIGGER after_add_or_delete_role
	AFTER INSERT OR DELETE OR UPDATE
	ON public.resource
	FOR EACH ROW
	EXECUTE PROCEDURE public.after_add_or_delete_role();
-- ddl-end --

-- object: public.after_update_schema | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.after_update_schema() CASCADE;
CREATE FUNCTION public.after_update_schema ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
BEGIN
	DELETE FROM schema_parent
	WHERE schema_id = OLD.id;
	INSERT INTO schema_parent(schema_id, parent_id, "order") 
	select r.id, (s #>> '{id}')::integer, ROW_NUMBER() OVER()
	from resource r join json_array_elements((r.content->'parents')::json) s on true
	where r.id = NEW.id;
	return null;
END;
$$;
-- ddl-end --
ALTER FUNCTION public.after_update_schema() OWNER TO postgres;
-- ddl-end --

-- object: after_update_schema | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_update_schema ON public.resource CASCADE;
CREATE TRIGGER after_update_schema
	AFTER UPDATE
	ON public.resource
	FOR EACH ROW
	WHEN ((old.schema_id = 1))
	EXECUTE PROCEDURE public.after_update_schema();
-- ddl-end --

-- object: public.field | type: TABLE --
-- DROP TABLE IF EXISTS public.field CASCADE;
CREATE TABLE public.field (
	id integer NOT NULL DEFAULT nextval('public.field_id_seq'::regclass),
	label varchar(255) NOT NULL,
	type varchar(255) NOT NULL,
	owner varchar(255),
	peer_id integer,
	multiple bool DEFAULT false,
	required bool DEFAULT false,
	validators jsonb,
	default_value jsonb,
	priv boolean DEFAULT false,
	description text,
	editable bool DEFAULT false,
	example jsonb,
	CONSTRAINT field_pk PRIMARY KEY (id)
);
-- ddl-end --
ALTER TABLE public.field OWNER TO postgres;
-- ddl-end --

-- object: field_label_idx | type: INDEX --
-- DROP INDEX IF EXISTS public.field_label_idx CASCADE;
CREATE INDEX field_label_idx ON public.field
USING btree
(
	label
);
-- ddl-end --

-- object: public.field_schema | type: TABLE --
-- DROP TABLE IF EXISTS public.field_schema CASCADE;
CREATE TABLE public.field_schema (
	field_id integer NOT NULL,
	schema_id integer NOT NULL,
	CONSTRAINT field_schema_pk PRIMARY KEY (field_id,schema_id)
);
-- ddl-end --
ALTER TABLE public.field_schema OWNER TO postgres;
-- ddl-end --

-- object: public.manage_delete_relationship | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.manage_delete_relationship() CASCADE;
CREATE FUNCTION public.manage_delete_relationship ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
    record RECORD;
BEGIN
  -- get all fields whose type is the schema (or inherited schema) of the deleted resource
  DROP TABLE IF EXISTS temp_resource_fields;
  CREATE TEMPORARY TABLE temp_resource_fields AS
  SELECT fs.schema_id as schema_id, 
         f.label as field_label, 
         f.multiple as multiple
  FROM   resource s, 
         schema_inheritance si, 
         field f,
         field_schema fs
  WHERE  si.descendant_id= OLD.schema_id AND 
         s.id = si.ancestor_id  AND
         f.id = fs.field_id AND
         f.type = s.label;

  -- delete many relationship
  FOR record IN
      SELECT * FROM temp_resource_fields
  LOOP  
      UPDATE resource r
      SET    content =content #- ('{' ||r2.field_label  || ',' || r2.pos || '}')::text[], updated_by=OLD.updated_by, updated_at=OLD.updated_at
      FROM (
        SELECT   r.id, ordinality - 1 AS pos, record.field_label as field_label
        FROM     resource r, jsonb_array_elements(r.content->record.field_label) with ordinality
        WHERE r.schema_id = record.schema_id
              AND (value->>'id')::integer = OLD.id  
              AND record.multiple is TRUE
              AND r.content #>> ('{' || record.field_label  || '}')::text[] NOT LIKE '${%}'
        ) r2
      WHERE r2.id = r.id;
  END LOOP;

  -- delete one relationship
  FOR record IN
      SELECT * FROM temp_resource_fields
  LOOP  
      UPDATE resource r
      SET    content = jsonb_set(r.content, ('{' || record.field_label  || '}')::text[], 'null'::jsonb), updated_by=OLD.updated_by, updated_at=OLD.updated_at
      FROM (
        SELECT   r.id
        FROM     resource r, jsonb_each(r.content) fields
        WHERE r.schema_id = record.schema_id
        AND fields.key = record.field_label
              AND (fields.value->>'id')::integer = OLD.id  
              AND record.multiple is NOT TRUE
        ) r2
      WHERE r2.id = r.id;           
  END LOOP;
  DROP TABLE IF EXISTS temp_resource_fields;
  RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.manage_delete_relationship() OWNER TO postgres;
-- ddl-end --

-- object: public.manage_update_relationship | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.manage_update_relationship() CASCADE;
CREATE FUNCTION public.manage_update_relationship ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
    record RECORD;
BEGIN

  DROP TABLE IF EXISTS temp_manage_relationship;
  CREATE TEMPORARY TABLE temp_manage_relationship AS
  SELECT  NEW.id as id, s.label as schema_label, f.label as field_label, p.label as peer_label, f.multiple as multiple, p.multiple as peer_multiple
  FROM   resource s,
        field_schema fs,
        field f,
        field p
  WHERE NEW.schema_id = s.id AND
        fs.schema_id = NEW.schema_id  AND
        fs.field_id = f.id AND
        f.peer_id = p.id;
        
  
  -- delete many relationship
  FOR record IN
      SELECT * FROM temp_manage_relationship
  LOOP  
      WITH temp_manage_relationship_resource_id AS (
              SELECT r.id, r.link_id from resource r
              WHERE  r.id IN (
                       SELECT (jsonb_array_elements(OLD.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND OLD.content ->> record.field_label is not null
                       UNION
                       SELECT (OLD.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND OLD.content ->> record.field_label is not null
                     ) AND
                     r.id not in (
                       SELECT (jsonb_array_elements(NEW.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND NEW.content ->> record.field_label is not null
                       UNION
                       SELECT (NEW.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND NEW.content ->> record.field_label is not null
                     )
              )
      UPDATE resource r
      SET    content =content #- ('{' ||r2.peer_label  || ',' || r2.pos || '}')::text[], updated_by=NEW.updated_by
      FROM (
        SELECT   r.id, ordinality - 1 AS pos, record.peer_label as peer_label
        FROM     resource r, jsonb_array_elements(r.content->record.peer_label) with ordinality
        WHERE r.id IN ( SELECT 
                          CASE WHEN link_id is null THEN id
                               ELSE link_id
                          END
                        FROM temp_manage_relationship_resource_id)
              AND (value->>'id')::integer = OLD.id  
              AND record.peer_multiple is TRUE
        ) r2
      WHERE r2.id = r.id;
  END LOOP;

  -- delete one relationship
  FOR record IN
      SELECT * FROM temp_manage_relationship
  LOOP  
      WITH temp_manage_relationship_resource_id AS (
              SELECT r.id, r.link_id from resource r
              WHERE    r.id IN (
                        SELECT (jsonb_array_elements(OLD.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND OLD.content ->> record.field_label is not null
                        UNION
                        SELECT (OLD.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND OLD.content ->> record.field_label is not null
                      ) AND
                      r.id not in (
                        SELECT (jsonb_array_elements(NEW.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND NEW.content ->> record.field_label is not null
                        UNION
                        SELECT (NEW.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND NEW.content ->> record.field_label is not null
                      )
              )
      UPDATE resource r
      SET    content = jsonb_set(r.content, ('{' || record.peer_label  || '}')::text[], 'null'::jsonb), updated_by=NEW.updated_by, updated_at=NEW.updated_at
      WHERE r.id IN ( SELECT 
                      CASE WHEN link_id is null THEN id
                           ELSE link_id
                      END
                    FROM temp_manage_relationship_resource_id)
          AND record.peer_multiple is not TRUE;                
  END LOOP;


  -- add relationship
  FOR record IN
      SELECT * FROM temp_manage_relationship
  LOOP
      WITH temp_manage_relationship_resource_id AS (
              SELECT r.id, r.link_id from resource r
              WHERE r.id IN (
                  SELECT (jsonb_array_elements(NEW.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND NEW.content ->> record.field_label is not NULL
                  UNION
                  SELECT (NEW.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND NEW.content ->> record.field_label is not NULL
              )AND
                  r.id NOT IN (
                  SELECT (jsonb_array_elements(OLD.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND OLD.content ->> record.field_label is not NULL
                  UNION
                  SELECT (OLD.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND OLD.content ->> record.field_label is not NULL
              )
          )    
      UPDATE   resource r
      SET        content = (
                CASE
                      WHEN r.content->record.peer_label IS NOT NULL AND record.peer_multiple IS TRUE
                      THEN jsonb_insert(r.content, ('{' || record.peer_label || ', 0}')::text[], json_build_object('id', NEW.id, '@s', NEW.schema_id, '@t', NEW.tenant_id, '@l', NEW.label )::jsonb)
                      WHEN r.content->record.peer_label  IS NULL AND record.peer_multiple IS TRUE
                      THEN jsonb_set(r.content,('{' ||record.peer_label  || '}')::text[], json_build_array(json_build_object('id', NEW.id, '@s', NEW.schema_id, '@t', NEW.tenant_id, '@l', NEW.label ))::jsonb)
                  ELSE jsonb_set(r.content,('{' ||record.peer_label  || '}')::text[], json_build_object('id', NEW.id, '@s', NEW.schema_id, '@t', NEW.tenant_id, '@l', NEW.label )::jsonb)
                  END
              ), updated_by=NEW.updated_by, updated_at=NEW.updated_at
      WHERE   r.id IN ( SELECT 
                          CASE WHEN link_id is null THEN id
                               ELSE link_id
                          END
                      FROM temp_manage_relationship_resource_id)
             AND (NOT( r.content ? record.peer_label ) OR NOT r.content->record.peer_label @> ('[{"id": ' || NEW.id ||'}]')::jsonb);
  END LOOP;
  DROP TABLE IF EXISTS temp_manage_relationship;
  RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.manage_update_relationship() OWNER TO postgres;
-- ddl-end --

-- object: public.manage_insert_relationship | type: FUNCTION --
-- DROP FUNCTION IF EXISTS public.manage_insert_relationship() CASCADE;
CREATE FUNCTION public.manage_insert_relationship ()
	RETURNS trigger
	LANGUAGE plpgsql
	VOLATILE 
	CALLED ON NULL INPUT
	SECURITY INVOKER
	PARALLEL UNSAFE
	COST 1
	AS $$
DECLARE
    record RECORD;
BEGIN

  DROP TABLE IF EXISTS temp_manage_relationship;
  CREATE TEMPORARY TABLE temp_manage_relationship AS
  SELECT  NEW.id as id, s.label as schema_label, f.label as field_label, p.label as peer_label, f.multiple as multiple, p.multiple as peer_multiple
  FROM   resource s,
        field_schema fs,
        field f,
        field p
  WHERE NEW.schema_id = s.id AND
        fs.schema_id = NEW.schema_id  AND
        fs.field_id = f.id AND
        f.peer_id = p.id;
  
  FOR record IN
      SELECT * FROM temp_manage_relationship
  LOOP
      WITH temp_manage_relationship_resource_id AS (
              SELECT r.id, r.link_id from resource r
              WHERE r.id IN (
                  SELECT (jsonb_array_elements(NEW.content->record.field_label) ->> 'id')::integer WHERE record.multiple IS TRUE AND NEW.content ->> record.field_label is not NULL
                  UNION
                  SELECT (NEW.content->record.field_label->> 'id')::integer WHERE record.multiple IS NOT TRUE AND NEW.content ->> record.field_label is not NULL
              )
          )
      UPDATE   resource r
      SET      content = (
                    CASE
                      WHEN r.content->record.peer_label IS NOT NULL AND record.peer_multiple IS TRUE
                      THEN jsonb_insert(r.content, ('{' || record.peer_label || ', 0}')::text[], json_build_object('id', NEW.id, '@s', NEW.schema_id, '@t', NEW.tenant_id, '@l', NEW.label )::jsonb)
                      WHEN r.content->record.peer_label  IS NULL AND record.peer_multiple IS TRUE
                      THEN jsonb_set(r.content,('{' ||record.peer_label  || '}')::text[], json_build_array(json_build_object('id', NEW.id, '@s', NEW.schema_id, '@t', NEW.tenant_id, '@l', NEW.label ))::jsonb)
                    ELSE jsonb_set(r.content,('{' ||record.peer_label  || '}')::text[], json_build_object('id', NEW.id, '@s', NEW.schema_id, '@t', NEW.tenant_id, '@l', NEW.label )::jsonb)
                    END
              ), updated_by=NEW.created_by, updated_at=NEW.created_at
      WHERE r.id IN ( SELECT 
                          CASE WHEN link_id is null THEN id
                               ELSE link_id
                          END
                      FROM temp_manage_relationship_resource_id)
           AND (NOT( r.content ? record.peer_label ) OR NOT r.content->record.peer_label @> ('[{"id": ' || NEW.id ||'}]')::jsonb);                        
  END LOOP; 

  DROP TABLE IF EXISTS temp_manage_relationship;

  RETURN NULL;

END;
$$;
-- ddl-end --
ALTER FUNCTION public.manage_insert_relationship() OWNER TO postgres;
-- ddl-end --

-- object: after_insert_resource_content | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_insert_resource_content ON public.resource CASCADE;
CREATE TRIGGER after_insert_resource_content
	AFTER INSERT 
	ON public.resource
	FOR EACH ROW
	EXECUTE PROCEDURE public.manage_insert_relationship();
-- ddl-end --

-- object: after_update_resource_content | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_update_resource_content ON public.resource CASCADE;
CREATE TRIGGER after_update_resource_content
	AFTER UPDATE
	ON public.resource
	FOR EACH ROW
	WHEN (((new.content <> old.content)
	and (pg_trigger_depth() < 1)))
	EXECUTE PROCEDURE public.manage_update_relationship();
-- ddl-end --

-- object: after_delete_resource_content | type: TRIGGER --
-- DROP TRIGGER IF EXISTS after_delete_resource_content ON public.resource CASCADE;
CREATE TRIGGER after_delete_resource_content
	AFTER DELETE 
	ON public.resource
	FOR EACH ROW
	EXECUTE PROCEDURE public.manage_delete_relationship();
-- ddl-end --

-- object: role_schema_role_fk | type: CONSTRAINT --
-- ALTER TABLE public.role_schema DROP CONSTRAINT IF EXISTS role_schema_role_fk CASCADE;
ALTER TABLE public.role_schema ADD CONSTRAINT role_schema_role_fk FOREIGN KEY (role_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_ace_ace_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource_ace DROP CONSTRAINT IF EXISTS resource_ace_ace_fk CASCADE;
ALTER TABLE public.resource_ace ADD CONSTRAINT resource_ace_ace_fk FOREIGN KEY (ace_id)
REFERENCES public.ace (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_ace_identity_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource_ace DROP CONSTRAINT IF EXISTS resource_ace_identity_fk CASCADE;
ALTER TABLE public.resource_ace ADD CONSTRAINT resource_ace_identity_fk FOREIGN KEY (identity_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_ace_resource_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource_ace DROP CONSTRAINT IF EXISTS resource_ace_resource_fk CASCADE;
ALTER TABLE public.resource_ace ADD CONSTRAINT resource_ace_resource_fk FOREIGN KEY (resource_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_ace_role_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource_ace DROP CONSTRAINT IF EXISTS resource_ace_role_fk CASCADE;
ALTER TABLE public.resource_ace ADD CONSTRAINT resource_ace_role_fk FOREIGN KEY (role_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: group_membership_parent_fk | type: CONSTRAINT --
-- ALTER TABLE public.group_membership DROP CONSTRAINT IF EXISTS group_membership_parent_fk CASCADE;
ALTER TABLE public.group_membership ADD CONSTRAINT group_membership_parent_fk FOREIGN KEY (parent_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: group_membership_child_fk | type: CONSTRAINT --
-- ALTER TABLE public.group_membership DROP CONSTRAINT IF EXISTS group_membership_child_fk CASCADE;
ALTER TABLE public.group_membership ADD CONSTRAINT group_membership_child_fk FOREIGN KEY (child_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_tag_resource_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource_tag DROP CONSTRAINT IF EXISTS resource_tag_resource_fk CASCADE;
ALTER TABLE public.resource_tag ADD CONSTRAINT resource_tag_resource_fk FOREIGN KEY (resource_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_tag_tag_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource_tag DROP CONSTRAINT IF EXISTS resource_tag_tag_fk CASCADE;
ALTER TABLE public.resource_tag ADD CONSTRAINT resource_tag_tag_fk FOREIGN KEY (tag_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: role_tag_role_fk | type: CONSTRAINT --
-- ALTER TABLE public.role_tag DROP CONSTRAINT IF EXISTS role_tag_role_fk CASCADE;
ALTER TABLE public.role_tag ADD CONSTRAINT role_tag_role_fk FOREIGN KEY (role_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: ace_role_fk | type: CONSTRAINT --
-- ALTER TABLE public.ace DROP CONSTRAINT IF EXISTS ace_role_fk CASCADE;
ALTER TABLE public.ace ADD CONSTRAINT ace_role_fk FOREIGN KEY (role_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: ace_identity_fk | type: CONSTRAINT --
-- ALTER TABLE public.ace DROP CONSTRAINT IF EXISTS ace_identity_fk CASCADE;
ALTER TABLE public.ace ADD CONSTRAINT ace_identity_fk FOREIGN KEY (identity_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: ace_entity_fk | type: CONSTRAINT --
-- ALTER TABLE public.ace DROP CONSTRAINT IF EXISTS ace_entity_fk CASCADE;
ALTER TABLE public.ace ADD CONSTRAINT ace_entity_fk FOREIGN KEY (resource_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: ace_source_fk | type: CONSTRAINT --
-- ALTER TABLE public.ace DROP CONSTRAINT IF EXISTS ace_source_fk CASCADE;
ALTER TABLE public.ace ADD CONSTRAINT ace_source_fk FOREIGN KEY (source_id)
REFERENCES public.ace (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: schema_parent_schema_fk | type: CONSTRAINT --
-- ALTER TABLE public.schema_parent DROP CONSTRAINT IF EXISTS schema_parent_schema_fk CASCADE;
ALTER TABLE public.schema_parent ADD CONSTRAINT schema_parent_schema_fk FOREIGN KEY (schema_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: schema_parent_parent_fk | type: CONSTRAINT --
-- ALTER TABLE public.schema_parent DROP CONSTRAINT IF EXISTS schema_parent_parent_fk CASCADE;
ALTER TABLE public.schema_parent ADD CONSTRAINT schema_parent_parent_fk FOREIGN KEY (parent_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_link_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource DROP CONSTRAINT IF EXISTS resource_link_fk CASCADE;
ALTER TABLE public.resource ADD CONSTRAINT resource_link_fk FOREIGN KEY (link_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: resource_tenant_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource DROP CONSTRAINT IF EXISTS resource_tenant_fk CASCADE;
ALTER TABLE public.resource ADD CONSTRAINT resource_tenant_fk FOREIGN KEY (tenant_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE NO ACTION ON UPDATE NO ACTION;
-- ddl-end --

-- object: resource_schema_fk | type: CONSTRAINT --
-- ALTER TABLE public.resource DROP CONSTRAINT IF EXISTS resource_schema_fk CASCADE;
ALTER TABLE public.resource ADD CONSTRAINT resource_schema_fk FOREIGN KEY (schema_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: role_hierarchy_parent_fk | type: CONSTRAINT --
-- ALTER TABLE public.role_hierarchy DROP CONSTRAINT IF EXISTS role_hierarchy_parent_fk CASCADE;
ALTER TABLE public.role_hierarchy ADD CONSTRAINT role_hierarchy_parent_fk FOREIGN KEY (parent_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: role_hierarchy_child_fk | type: CONSTRAINT --
-- ALTER TABLE public.role_hierarchy DROP CONSTRAINT IF EXISTS role_hierarchy_child_fk CASCADE;
ALTER TABLE public.role_hierarchy ADD CONSTRAINT role_hierarchy_child_fk FOREIGN KEY (child_id)
REFERENCES public.resource (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: field_peer_fk | type: CONSTRAINT --
-- ALTER TABLE public.field DROP CONSTRAINT IF EXISTS field_peer_fk CASCADE;
ALTER TABLE public.field ADD CONSTRAINT field_peer_fk FOREIGN KEY (peer_id)
REFERENCES public.field (id) MATCH FULL
ON DELETE SET NULL ON UPDATE NO ACTION;
-- ddl-end --

-- object: field_schema_field_fk | type: CONSTRAINT --
-- ALTER TABLE public.field_schema DROP CONSTRAINT IF EXISTS field_schema_field_fk CASCADE;
ALTER TABLE public.field_schema ADD CONSTRAINT field_schema_field_fk FOREIGN KEY (field_id)
REFERENCES public.field (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: field_schema_resource_fk | type: CONSTRAINT --
-- ALTER TABLE public.field_schema DROP CONSTRAINT IF EXISTS field_schema_resource_fk CASCADE;
ALTER TABLE public.field_schema ADD CONSTRAINT field_schema_resource_fk FOREIGN KEY (schema_id)
REFERENCES public.resource (id) MATCH SIMPLE
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --


