package net.oneki.mtac.core.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.framework.AbstractRepository;
import net.oneki.mtac.core.util.rowmapper.ResourceRowMapper;
import net.oneki.mtac.core.util.sql.ReactiveSqlUtils;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.json.EntityMapper;
import net.oneki.mtac.framework.query.Filter;
import net.oneki.mtac.framework.query.FilterCriteria;
import net.oneki.mtac.framework.query.Query;
import net.oneki.mtac.framework.query.Relation;
import net.oneki.mtac.framework.query.SortCriteria;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.util.SetUtils;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.core.util.security.SecurityUtils;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.Resource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ResourceReactiveRepository extends AbstractRepository {
    private final SecurityContext securityContext;
    @Qualifier("entityMapper")
    private final EntityMapper entityMapper;

    // ------------------------------------------------- CREATE
    @Transactional
    public <T extends Resource> Mono<T> create(T resource) {
        resource.setCreatedBy(securityContext != null ? securityContext.getUsername() : "");
        Map<String, Object> parameters = SqlUtils.getPropertySqlParameters(resource);

        try {
            var json = entityMapper.writeValueAsString(resource);
            parameters.put("content", json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return create(resource, parameters);
    }

    protected <T extends Resource> Mono<T> create(T resource,
            Map<String, ?> parameters) {
        var sql = SqlUtils.getSQL("resource/resource_insert.sql");
        return db.sql(sql).bindValues(parameters)
                .fetch()
                .first()
                .map(row -> {
                    resource.setId((Integer) row.get("id"));
                    return resource;
                });
    }

    public Mono<Integer> createLink(Resource source, Integer targetTenantId, LinkType linkType, boolean pub,
            String createdBy) {
        return ReactiveSqlUtils.getReactiveSQL("resource/resource_insert_link.sql")
                .flatMap(sql -> {
                    return db.sql(sql)
                            .bind("id", source.getId())
                            .bind("schemaLabel", source.getSchemaLabel())
                            .bind("tenantId", targetTenantId)
                            .bind("schemaId", Constants.SCHEMA_SCHEMA_ID)
                            .bind("targetTenantId", targetTenantId)
                            .bind("targetSchemaLabel", source.getSchemaLabel())
                            .bind("targetSchemaId", Constants.SCHEMA_SCHEMA_ID)
                            .bind("targetLinkType", linkType.ordinal())
                            .bind("targetPub", pub)
                            .bind("createdBy", createdBy)
                            .fetch()
                            .first()
                            .map(row -> {
                                return (Integer) row.get("id");
                            });
                });
    }

    // ------------------------------------------------- UPDATE

    public <T extends Resource> Mono<T> update(T resource) {

        Map<String, Object> parameters = SqlUtils.getPropertySqlParameters(resource);
        try {
            var json = entityMapper.writeValueAsString(resource);
            parameters.put("content", json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return ReactiveSqlUtils.getReactiveSQL("resource/resource_update.sql")
                .flatMap(sql -> {
                    return db.sql(sql)
                            .bindValues(parameters)
                            .fetch()
                            .first()
                            .then(Mono.just(resource));
                });
    }

    // ------------------------------------------------- DELETE

    public Mono<Void> delete(Integer id) {
        if (id != null) {
            return delete(SetUtils.of(id));
        }
        return Mono.empty();
    }

    public Mono<Void> delete(Collection<Integer> ids) {
        var sql = "DELETE FROM resource WHERE id IN (:ids)";
        if (ids != null && ids.size() > 0) {
            Map<String, Object> args = new HashMap<>();
            args.put("ids", ids);
            return db.sql(sql)
                    .bindValues(args)
                    .fetch()
                    .first()
                    .then();
        }
        return Mono.empty();
    }

    public <T extends Resource> Mono<Void> delete(String tenantLabel, String label, Class<T> resourceClass) {
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        var schemaId = ResourceRegistry.getSchemaId(ResourceRegistry.getSchemaByClass(resourceClass));
        var sql = "DELETE FROM resource WHERE label = :label AND tenant_id = :tenantId AND schema_id = :schemaId";
        var args = new HashMap<String, Object>();
        args.put("label", label);
        args.put("tenantId", tenantId);
        args.put("schemaId", schemaId);
        return db.sql(sql)
                .bindValues(args)
                .fetch()
                .first()
                .then();
    }

    // ------------------------------------------------- GET BY ID

    public <T extends Resource> Mono<T> getById(int id, Class<T> resultContentClass) {
        return getById(id, resultContentClass, null, null);
    }

    public <T extends Resource> Mono<T> getById(int id, Class<T> resultContentClass, Mono<String> sql) {
        return getById(id, resultContentClass, sql, null);
    }

    public <T extends Resource> Mono<T> getByIdUnsecure(int id, Class<T> resultContentClass) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_id_unsecure.sql");
        return getById(id, resultContentClass, sql);
    }

    public <T extends Resource> Mono<T> getById(int id, Class<T> resultContentClass, Mono<String> sql,
            Map<String, Object> args) {
        if (sql == null) {
            sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_id.sql");
        }
        final var parameters = (args != null) ? args : new HashMap<String, Object>();

        parameters.put("id", id);
        // sids represent the id of the logged-in user + all the groups in which he/she
        // is present
        if (securityContext != null) {
            parameters.put("sids", securityContext.getSids());
        }

        return sql.flatMap(sqlQuery -> {
            return db.sql(sqlQuery)
                    .bindValues(parameters)
                    .fetch()
                    .first()
                    .map(ResourceRowMapper::mapRow);
        });

    }

    // ------------------------------------------------- GET BY URN
    public <T extends Resource> Mono<T> getByLabelOrUrn(String labelOrUrn, Class<T> resultContentClass) {
        if (labelOrUrn.startsWith("urn:")) {
            return getByUrn(labelOrUrn, resultContentClass);
        } else {
            return getByUniqueLabel(labelOrUrn, resultContentClass);
        }
    }

    public <T extends Resource> Mono<T> getByUrn(String urn, Class<T> resultContentClass) {
        return getByUrn(urn, resultContentClass, null, null);
    }

    public <T extends Resource> Mono<T> getByUrn(String urn, Class<T> resultContentClass, Mono<String> sql) {
        return getByUrn(urn, resultContentClass, sql, null);
    }

    public <T extends Resource> Mono<T> getByUrn(String urn, Class<T> resultContentClass, Mono<String> sql,
            Map<String, Object> args) {
        if (sql == null) {
            sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_urn.sql");
        }
        final var parameters = (args != null) ? args : new HashMap<String, Object>();
        parameters.put("urn", urn);
        // sids represent the id of the logged-in user + all the groups in which he/she
        // is present
        if (securityContext != null) {
            parameters.put("sids", securityContext.getSids());
        }

        return sql.flatMap(sqlQuery -> {
            return db.sql(sqlQuery)
                    .bindValues(parameters)
                    .fetch()
                    .first()
                    .map(ResourceRowMapper::mapRow);
        });

    }

    // ------------------------------------------------- GET BY LABEL

    public <T extends Resource> Mono<T> getByLabel(String label, String tenantLabel,
            Class<T> resultContentClass) {
        return getByLabel(label, tenantLabel, resultContentClass, null, null);
    }

    public <T extends Resource> Mono<T> getByLabel(String label, String tenantLabel,
            Class<T> resultContentClass, Mono<String> sql) {
        return getByLabel(label, tenantLabel, resultContentClass, sql, null);
    }

    public <T extends Resource> Mono<T> getByUniqueLabel(String label,
            Class<T> resultContentClass) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_label_unique.sql");
        return getByLabel(label, null, resultContentClass, sql, null);
    }

    public <T extends Resource> Mono<T> getByUniqueLabelUnsecure(String label,
            Class<T> resultContentClass) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_label_unique_unsecure.sql");
        return getByLabel(label, null, resultContentClass, sql, null);
    }

    public <T extends Resource> Mono<T> getByLabel(String label, String tenantLabel,
            Class<T> resultContentClass, Mono<String> sql, Map<String, Object> args) {
        if (sql == null) {
            sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_label.sql");
        }
        final var parameters = (args != null) ? args : new HashMap<String, Object>();
        var schemaLabel = ResourceRegistry.getSchemaByClass(resultContentClass);
        parameters.put("label", label);
        parameters.put("schemaLabel", schemaLabel);
        if (tenantLabel != null) {
            var tenantId = ResourceRegistry.getTenantId(tenantLabel);
            if (tenantId == null) {
                throw new BusinessException("TENANT_NOT_FOUND", "Tenant not found for label " + tenantLabel);
            }
            parameters.put("tenantId", tenantId);
        }

        // sids represent the id of the logged-in user + all the groups in which he/she
        // is present
        if (securityContext != null) {
            parameters.put("sids", securityContext.getSids());
        }

        return sql.flatMap(sqlQuery -> {
            return db.sql(sqlQuery)
                    .bindValues(parameters)
                    .fetch()
                    .first()
                    .map(ResourceRowMapper::mapRow);
        });

    }

    // ------------------------------------------------- LIST
    public <T extends Resource> Flux<T> listByTypeUnsecure(Class<T> resultContentClass,
            Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_by_schema_unsecure.sql");
        var result = list(Constants.TENANT_ROOT_ID, resultContentClass, sql, query);
        return result;
    }

    public Flux<Resource> listByTenant(int tenantId, Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_by_tenant.sql");
        var result = list(tenantId, Resource.class, sql, query);
        return result;
    }

    public Flux<Resource> listByTenant(String tenantLabel, Query query) {
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        return listByTenant(tenantId, query);
    }

    public Flux<Resource> listByTenantUnsecure(int tenantId, Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_by_tenant_unsecure.sql");
        var result = list(tenantId, Resource.class, sql, query);
        return result;
    }

    public Flux<Resource> listByTenantUnsecure(String tenantLabel, Query query) {
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        return listByTenantUnsecure(tenantId, query);
    }

    public Flux<Resource> listAll(Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_all.sql");
        var result = list(null, Resource.class, sql, query);
        return result;
    }

    public Flux<Resource> listAllUnsecure(Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_all_unsecure.sql");
        var result = list(null, Resource.class, sql, query);
        return result;
    }

    public <T extends Resource> Flux<T> listByTenantAndType(String tenantLabel,
            Class<T> resultContentClass,
            Query query) {
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        return listByTenantAndType(tenantId, resultContentClass, query);
    }

    public <T extends Resource> Flux<T> listByTenantAndType(int tenantId,
            Class<T> resultContentClass,
            Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_by_tenant_and_schema.sql");
        var result = list(tenantId, resultContentClass, sql, query);
        return result;
    }

    public <T extends Resource> Flux<T> listByTenantAndTypeUnsecure(String tenantLabel,
            Class<T> resultContentClass,
            Query query) {
        var tenantId = ResourceRegistry.getTenantId(tenantLabel);
        return listByTenantAndTypeUnsecure(tenantId, resultContentClass, query);
    }

    public <T extends Resource> Flux<T> listByTenantAndTypeUnsecure(int tenantId,
            Class<T> resultContentClass,
            Query query) {
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_list_by_tenant_and_schema_unsecure.sql");
        var result = list(tenantId, resultContentClass, sql, query);
        return result;
    }

    // ------------------------------------------------- GENERIC LIST

    public <T extends Resource> Flux<T> list(Integer tenantId, Class<T> contentClass,
            Mono<String> sql) {
        return list(tenantId, contentClass, sql, null, null);
    }

    public <T extends Resource> Flux<T> list(Integer tenantId, Class<T> contentClass,
            Mono<String> sql, Query query) {
        return list(tenantId, contentClass, sql, query, null);
    }

    public <T extends Resource> Flux<T> list(Integer tenantId, Class<T> contentClass,
            Mono<String> sql, Query query,
            Map<String, Object> args) {

        var schemaLabel = ResourceRegistry.getSchemaByClass(contentClass);
        var parameters = (args != null) ? args : new HashMap<String, Object>();

        Map<String, String> tableAliases = new HashMap<>();
        // We need to perform joins from the resource table to the resource table if we
        // need to retrieve information about the schema or the tenant.
        // To do this, we need to specify an alias for each join
        tableAliases.put("resource", "r");
        tableAliases.put("tenant", "t");
        tableAliases.put("schema", "s");
        tableAliases.put("link", "l");
        String relationSql = null;

        if (tenantId != null) {
            // relationSQL allows us to filter a field that's in a relation
            // For example, we want to retrieve users who are members of a group whose
            // description is "admin"
            // In the query, we'd have filter=memberOf.description eq admin
            // To do this, we need to build a temporary table in the SQL query
            relationSql = SqlUtils.getSQL("resource/resource_list_relations_by_tenant.sql");
        }

        String tableSql = "";
        String leftJoinTableSql = "";

        // sids represent the id of the logged-in user + all the groups in which he/she
        // is present
        if (securityContext != null) {
            parameters.put("sids", securityContext.getSids());
            parameters.put("tenantSids", SecurityUtils.getTenantSids(securityContext));
        }

        // schemaLabel is the type of the object we want in return.
        // For example, we want a list of Users, so schemaLabel = "iam.identiy.user".
        parameters.put("schemaLabel", schemaLabel);
        // tenantId is the id of the container to search from
        // For example, if we want to search for the users of a company, tenantId = 22
        // (= the resource with id 22 representing tenant company1).
        parameters.put("tenantId", tenantId);
        parameters.put("schemaSchemaId", Constants.SCHEMA_SCHEMA_ID);
        String temporaryTablesSql = "";
        String filterSql = "";
        Map<String, String> sortDef = null;
        if (query == null)
            query = new Query();
        if (query.getFilter() != null) {
            Map<String, String> filterDef = query.getFilter().toSql(tableAliases, parameters);
            filterSql += " AND " + filterDef.get("where");
            tableSql += filterDef.get("table");
            leftJoinTableSql += filterDef.get("leftJoinTable");
        }
        if (relationSql != null) {
            if (query.getFilter() != null) {
                for (Entry<String, FilterCriteria> entry : query.getFilter().getRelations().entrySet()) {
                    // We look at all the relationships in the filter and create a temporary table
                    // for each relationship.
                    // In the .sql file, we have placeholder named <UNIQUE_NAME> et we replace it
                    // with the unique name of the relationship
                    String temporaryTableSql = relationSql.replaceAll("<UNIQUE_NAME>", entry.getKey());
                    if (entry.getValue() == null) {
                        throw new RuntimeException("Cannot find a valid filter criteria in the relation");
                    }
                    // We create a filter for the relationship
                    // Example: primaryGroup.description = 'admin' -> primaryGroup is a relation
                    // We are going to create a temporary table 'table1'.
                    // To create this table, we execute SQL id FROM resource WHERE schema_id = 7 AND
                    // content #>> '{description}' = 'admin'
                    // We need the create the filter: type=ResourceRegistryType of type group,
                    // criteria=schema_id = 7 AND content #>> '{description}' = 'admin'
                    Filter relationFilter = Filter.builder()
                            .type(entry.getValue().getResourceDesc())
                            .context(query.getFilter().getContext())
                            .build();
                    relationFilter.addCriteria(entry.getValue());
                    Map<String, String> temporaryTableFilterDef = relationFilter.toSql(tableAliases, parameters);
                    String temporaryTableFilterSql = " AND " + temporaryTableFilterDef.get("where");
                    temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic filters />\"",
                            temporaryTableFilterSql);
                    temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic left join tables />\"",
                            temporaryTableFilterDef.get("leftJoinTable"));
                    temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic tables />\"",
                            temporaryTableFilterDef.get("table"));
                    temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic fields />\"", "");
                    temporaryTableSql = temporaryTableSql.replace(
                            "\"<placeholder for dynamic relation order column />\"",
                            "");
                    temporaryTablesSql += temporaryTableSql;
                    parameters.put(entry.getKey() + "SchemaLabel", entry.getValue().getResourceDesc().getLabel());
                }
            }

            if (query.getSort() != null) {
                for (Entry<String, SortCriteria> entry : query.getSort().getRelations().entrySet()) {
                    String temporaryTableSql = relationSql.replaceAll("<UNIQUE_NAME>", entry.getKey());
                    String dynamicTables = "";
                    String dynamicLeftJoinTables = "";
                    if (entry.getValue() == null) {
                        throw new RuntimeException("Cannot find a valid sort criteria in the relation");
                    }
                    // // the master table will have a relation to the relation table
                    // // the relation table will do the ordering but since it can have itself a
                    // relation to another relation table, the result is safe in a rowNumber column
                    // via the ROW_NUMBER() function of Postgres
                    // // --> this rowNumber column is already sorted
                    // // at the end we will have WHERE r.id = <REL_TABLE>.id ORDER BY
                    // <REL_TABLE>.rowNumber ASC
                    // // the sortDef is used below and must only be defined for the first relation
                    // table (the first relation table is executed at the end since we need first
                    // the relation of this relation table, that's why sortDef is redefined for each
                    // occurence of the loop)
                    // sortDef = new HashMap<>();
                    // sortDef.put("table", entry.getKey());
                    // sortDef.put("order_by", entry.getKey() + ".rowNumber" + " ASC");

                    Map<String, String> relationSortDef = entry.getValue().toSql(tableAliases);
                    temporaryTableSql = temporaryTableSql.replace(
                            "\"<placeholder for dynamic relation order column />\"",
                            ", " + relationSortDef.get("operand") + " as \"" + SqlUtils.ROW_NUMBER_COLUMN + "\"");
                    dynamicTables += relationSortDef.get("table");
                    dynamicLeftJoinTables += relationSortDef.get("leftJoinTable");

                    Relation relation = entry.getValue().getRelation();
                    if (relation != null) {
                        Filter relationFilter = Filter.builder()
                                .type(entry.getValue().getType())
                                .context(query.getSort().getContext())
                                .build();
                        relationFilter.addCriteria(
                                new FilterCriteria(entry.getValue().getType(), FilterCriteria.Operator.EQUALS,
                                        relation.getField() + ".id", relation.getTable() + ".id", false, false, true));
                        Map<String, String> temporaryTableFilterDef = relationFilter.toSql(tableAliases, parameters);
                        String temporaryTableFilterSql = " AND " + temporaryTableFilterDef.get("where");
                        temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic filters />\"",
                                temporaryTableFilterSql);
                        dynamicTables += temporaryTableFilterDef.get("table");
                        dynamicLeftJoinTables += temporaryTableFilterDef.get("leftJoinTable");
                    } else {
                        temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic filters />\"", "");
                    }
                    temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic left join tables />\"",
                            dynamicLeftJoinTables);
                    temporaryTableSql = temporaryTableSql.replace("\"<placeholder for dynamic tables />\"",
                            dynamicTables);
                    parameters.put(entry.getKey() + "SchemaLabel", entry.getValue().getType().getLabel());
                    temporaryTablesSql += temporaryTableSql;
                }
            }
        }
        if (query.isNoLink()) {
            filterSql += " AND r.link_id is NULL";
        }
        if (query.isNoPublic()) {
            filterSql += " AND r.pub is not TRUE";
        }
        if (tenantId != null && query.isForceTenant()) {
            filterSql += " AND r.tenant_id IN (SELECT id FROM tmp_tenant_ancestor)";
        }

        String sortSql = "";
        String groupBySql = "";
        if (query.getSort() != null) {
            sortSql = " ORDER BY ";
            if (query.getSort() != null) {
                int index = 0;
                for (SortCriteria sortCriteria : query.getSort().getCriterias()) {
                    sortDef = sortCriteria.toSql(tableAliases);
                    tableSql += sortDef.get("table");
                    leftJoinTableSql += sortDef.get("leftJoinTable");
                    if (index > 0) {
                        sortSql += ",";
                    }
                    sortSql += sortDef.get("order_by");
                    Relation relation = sortCriteria.getRelation();
                    if (relation != null) {
                        Filter relationFilter = Filter.builder()
                                .type(sortCriteria.getType())
                                .context(query.getSort().getContext())
                                .build();
                        relationFilter
                                .addCriteria(new FilterCriteria(sortCriteria.getType(), FilterCriteria.Operator.EQUALS,
                                        relation.getField() + ".id", relation.getTable() + ".id", false, false, true));
                        Map<String, String> filterDef = relationFilter.toSql(tableAliases, parameters);
                        filterSql += " AND " + filterDef.get("where");
                        tableSql += filterDef.get("table");
                        leftJoinTableSql += filterDef.get("leftJoinTable");
                        groupBySql += "," + sortDef.get("operand");
                    }
                    index++;
                }
            }
        }

        final String groupBySqlFinal = groupBySql;
        final String filterSqlFinal = filterSql;
        final String leftJoinTableSqlFinal = leftJoinTableSql;
        final String tableSqlFinal = tableSql;
        final String sortSqlFinal = sortSql;
        final String temporaryTablesSqlFinal = temporaryTablesSql;
        final Query queryFinal = query;

        return sql
                .map(s -> {
                    s = s.replace("\"<placeholder for dynamic groupby />\"", groupBySqlFinal);
                    s = s.replace("\"<placeholder for dynamic filters />\"", filterSqlFinal);
                    s = s.replace("\"<placeholder for dynamic order />\"", sortSqlFinal);
                    String limitSql = " LIMIT " + ((queryFinal.getLimit() != null) ? queryFinal.getLimit().toString() : "ALL");
                    limitSql += " OFFSET " + ((queryFinal.getOffset() != null) ? queryFinal.getOffset().toString() : "0");

                    s = s.replace("\"<placeholder for dynamic left join tables />\"", leftJoinTableSqlFinal)
                            .replace("\"<placeholder for dynamic tables />\"", tableSqlFinal)
                            .replace("\"<placeholder for dynamic limit />\"", limitSql)
                            .replace("\"<placeholder for dynamic temporary tables />\"", temporaryTablesSqlFinal);

                    return s;
                })
                .flatMapMany(sqlQuery -> {
                    return db.sql(sqlQuery)
                            .bindValues(parameters)
                            .fetch()
                            .all()
                            .map(ResourceRowMapper::mapRow);
                });
    }

    public boolean isUnique(String label, String tenantLabel, Class<?> resourceClass, String scope) {
        String sql = "select is_unique(:label, :tenantId, :schemaId, :scope) as id";
        var args = new HashMap<String, Object>();
        args.put("label", label);
        args.put("scope", scope);
        args.put("schemaId", ResourceRegistry.getSchemaId(resourceClass));
        args.put("tenantId", ResourceRegistry.getTenantId(tenantLabel));

        var result = db.sql(sql)
                .bindValues(args)
                .fetch()
                .first()
                .map(row -> {
                    return (Integer) row.get("id");
                })
                .block();

        return result == null;
    }

    public Flux<Resource> listbyIds(Collection<Integer> ids) {
        final Map<String, Object> args = new HashMap<>();
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_ids.sql");
        args.put("ids", ids);
        if (securityContext != null) {
            args.put("sids", securityContext.getSids());
        }

        return sql
                .flatMapMany(sqlQuery -> {
                    return db.sql(sqlQuery)
                            .bindValues(args)
                            .fetch()
                            .all()
                            .map(ResourceRowMapper::mapRow);
                });
    }

    public Flux<Resource> listbyUrns(Collection<String> urns) {
        final Map<String, Object> args = new HashMap<>();
        var sql = ReactiveSqlUtils.getReactiveSQL("resource/resource_get_by_urns.sql");
        args.put("urns", urns);
        if (securityContext != null) {
            args.put("sids", securityContext.getSids());
        }

        return sql
                .flatMapMany(sqlQuery -> {
                    return db.sql(sqlQuery)
                            .bindValues(args)
                            .fetch()
                            .all()
                            .map(ResourceRowMapper::mapRow);
                });
    }
}
