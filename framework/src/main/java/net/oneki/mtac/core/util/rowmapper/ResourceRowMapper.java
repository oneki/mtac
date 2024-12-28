package net.oneki.mtac.core.util.rowmapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.core.security.Ace;
import net.oneki.mtac.core.security.Acl;
import net.oneki.mtac.core.util.cache.Cache;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.core.util.exception.UnexpectedException;
import net.oneki.mtac.core.util.json.JsonUtil;
import net.oneki.mtac.resource.Resource;
import net.oneki.mtac.resource.schema.Schema;

@RequiredArgsConstructor
public class ResourceRowMapper<T extends Resource> implements RowMapper<T> {
    private final ResourceRepository resourceRepository;

    @SuppressWarnings("unchecked")
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        String json = null;
        var schemaLabel = Cache.getInstance().getSchemaById(rs.getInt("schema_id")).getLabel();
        var clazz = (Class<T>) ResourceRegistry.getClassBySchema(schemaLabel);


        if (rs.getInt("link_id") == 0) {
            json = rs.getString("content");
        } else {
            json = rs.getString("link_content");
        }
        var resource = JsonUtil.json2Object(json, clazz);
        var relationFields = ResourceRegistry.getRelations(clazz);
        for (var relationField : relationFields) {
            relationField.getField().setAccessible(true);
            try {
                if (relationField.isMultiple()) {
                    var relations = (List<Resource>) relationField.getField().get(resource);
                    if (relations == null) continue;
                    //var relationEntities = new ArrayList<Resource>();
                    for (var relation : relations) {
                        relation.setUrn(String.format("urn:%s:%s:%s", 
                            ResourceRegistry.getTenantLabel(relation.getTenantId()), 
                            ResourceRegistry.getSchemaLabel(relation.getSchemaId()), 
                            relation.getLabel()));
                    }
                    //relationField.getField().set(resource, relationEntities);
                } else {
                    var relation = (Resource) relationField.getField().get(resource);
                    if (relation == null) continue;
                    relation.setUrn(String.format("urn:%s:%s:%s", 
                            ResourceRegistry.getTenantLabel(relation.getTenantId()), 
                            ResourceRegistry.getSchemaLabel(relation.getSchemaId()), 
                            relation.getLabel()));
                }
            } catch (IllegalAccessException e) {
                throw new SQLException(e);
            }
        }

        // resource.setUrn(String.format("%s:%s:%s", tenantLabel, schemaLabel, rs.getString("label")));

        ResultSetMetaData rsmd = rs.getMetaData();
        for (int col = 1; col <= rsmd.getColumnCount(); col++) {
            String columnName = rsmd.getColumnName(col);
            mapColumn(rs, columnName, resource);
        }



        return resource;
    }

    protected void mapColumn(ResultSet rs, String columnName, T resource)
            throws SQLException {
        String json;
        var schemaId = rs.getInt("schema_id");
        var schema = Cache.getInstance().getSchemaById(schemaId);

        if (schema == null) {
            schema = resourceRepository.getByIdUnsecure(schemaId, Schema.class);
            if (schema == null) {
                throw new UnexpectedException("SCHEMA_NOT_FOUND", "Schema not found for id " + schemaId);
            }
            Cache.getInstance().addSchema(schema);
        }
        var isLink = rs.getInt("link_id") != 0;
        switch (columnName) {
            case "id":
                columnName = isLink ? "link_id" : "id";
                resource.setId(rs.getInt(columnName));  
                break;
            case "urn":
                columnName = isLink ? "link_urn" : "urn";
                resource.setUrn(rs.getString(columnName));
                break;                
            case "pub":
                columnName = isLink ? "link_pub" : "pub";
                resource.setPub(rs.getBoolean(columnName));
                break;
            case "created_at":
                columnName = isLink ? "link_created_at" : "created_at";
                resource.setCreatedAt(
                        rs.getTimestamp(columnName) != null ? rs.getTimestamp(columnName).toInstant() : null);
                break;
            case "updated_at":
                columnName = isLink ? "link_updated_at" : "updated_at";
                resource.setUpdatedAt(
                        rs.getTimestamp(columnName) != null ? rs.getTimestamp(columnName).toInstant() : null);
                break;
            case "created_by":
                columnName = isLink ? "link_created_by" : "created_by";
                resource.setCreatedBy(rs.getString(columnName));
                break;
            case "updated_by":
                columnName = isLink ? "link_updated_by" : "updated_by";
                resource.setUpdatedBy(rs.getString(columnName));
                break;

            case "link_id":
                resource.setLink(!rs.wasNull());
                break;

            // case "tenant_id":
            //     columnName = isLink ? "link_tenant_id" : "tenant_id";
            //     var tenantId = rs.getInt(columnName);
            //     if (rs.wasNull()) {
            //         resource.setTenant(null);
            //     } else if (tenantId == Constants.TENANT_ROOT_ID) {
            //         resource.setTenant(Ref.builder()
            //                 .id(Constants.TENANT_ROOT_ID)
            //                 .label(Constants.TENANT_ROOT_LABEL)
            //                 .schema(Constants.TENANT_ROOT_SCHEMA_LABEL)
            //                 .build());
            //     } else {
            //         var tenant = Cache.getInstance().getTenantById(tenantId); 
            //         if (tenant == null) {
            //             tenant = resourceRepository.getByIdUnsecure(tenantId, TenantEntity.class);
            //             if (tenant == null) {
            //                 throw new UnexpectedException("TENANT_NOT_FOUND", "Tenant not found for id " + tenantId);
            //             }
            //             Cache.getInstance().addTenant(tenant);
            //         }
            //         resource.setTenant(Ref.builder()
            //                 .id(tenantId)
            //                 .label(tenant.getLabel())
            //                 .schema(tenant.getSchema())
            //                 .build());
            //     }

            //     break;
            // case "tags":
            // json = rs.getString(columnName);
            // if (json != null) {
            // Set<Tag> tags = new HashSet<>();
            // // cast to List<Map<String,Object>>
            // ObjectMapper mapper = new ObjectMapper();
            // try {
            // List<Map<String, Object>> tagMaps = mapper.readValue(json,
            // new TypeReference<List<Map<String, Object>>>() {
            // });
            // Set<Integer> ids = new HashSet<>();
            // for (Map<String, Object> tagMap : tagMaps) {
            // if (tagMap.getOrDefault("id", null) != null) {
            // Integer id = (Integer) tagMap.get("id");
            // if (!ids.contains(id)) {
            // Tag tag = Tag.tagBuilder().id(id).label((String)
            // tagMap.get("label")).build();
            // tags.add(tag);
            // ids.add(id);
            // }

            // }
            // }
            // } catch (Exception e) {
            // }
            // resource.setTags(tags);
            // }
            // break;
            case "acl":
                json = rs.getString(columnName);
                if (json != null) {
                    var acl = new Acl();
                    acl.addAll(JsonUtil.json2Set(json, Ace.class));
                    resource.setAcl(acl);
                } else {
                    resource.setAcl(new Acl());
                }
                break;
            default:
                break;
        }
    }
}