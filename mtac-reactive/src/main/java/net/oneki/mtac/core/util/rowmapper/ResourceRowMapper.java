package net.oneki.mtac.core.util.rowmapper;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.json.JsonEntityMapper;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.security.Ace;
import net.oneki.mtac.model.core.security.Acl;
import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.Resource;

@RequiredArgsConstructor
public class ResourceRowMapper {

    @SuppressWarnings("unchecked")
    public static <T extends Resource> T mapRow(Map<String, Object> row) {
        String json = null;
        var schemaLabel = ResourceRegistry.getSchemaById((Integer) row.get("schema_id")).getLabel();
        var clazz = (Class<T>) ResourceRegistry.getClassBySchema(schemaLabel);

        if ((Integer) row.get("link_id") == 0) {
            json = (String) row.get("content");
        } else if ((Integer) row.get("link_type") == LinkType.Ref.ordinal()) {
                json = "{}";
        } else {
            json = (String) row.get("link_content");
        }
        var resource = JsonEntityMapper.json2Object(json, clazz);
        var relationFields = ResourceRegistry.getRelations(clazz);
        for (var relationField : relationFields) {
            relationField.getField().setAccessible(true);
            try {
                if (relationField.isMultiple()) {
                    var relations = (List<Resource>) relationField.getField().get(resource);
                    if (relations == null)
                        continue;
                    // var relationEntities = new ArrayList<Resource>();
                    for (var relation : relations) {
                        relation.setUrn(String.format("urn:%s:%s:%s",
                                ResourceRegistry.getTenantLabel(relation.getTenantId()),
                                ResourceRegistry.getSchemaLabel(relation.getSchemaId()),
                                relation.getLabel()));
                    }
                    // relationField.getField().set(resource, relationEntities);
                } else {
                    var relation = (Resource) relationField.getField().get(resource);
                    if (relation == null)
                        continue;
                    relation.setUrn(String.format("urn:%s:%s:%s",
                            ResourceRegistry.getTenantLabel(relation.getTenantId()),
                            ResourceRegistry.getSchemaLabel(relation.getSchemaId()),
                            relation.getLabel()));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        // resource.setUrn(String.format("%s:%s:%s", tenantLabel, schemaLabel,
        // rs.getString("label")));

        for (var key : row.keySet()) {
            try {
                mapColumn(row, key, resource);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        return resource;
    }

    protected static <T extends Resource> void mapColumn(Map<String, Object> row, String columnName, T resource)
            throws SQLException {
        String json;
        // var schemaId = rs.getInt("schema_id");
        // var schema = Cache.getInstance().getSchemaById(schemaId);

        // if (schema == null) {
        // schema = resourceRepository.getByIdUnsecure(schemaId, Schema.class);
        // if (schema == null) {
        // throw new UnexpectedException("SCHEMA_NOT_FOUND", "Schema not found for id "
        // + schemaId);
        // }
        // Cache.getInstance().addSchema(schema);
        // }
        var isLink = (Integer) row.get("link_id") != 0;
        switch (columnName) {
            case "id":
                columnName = isLink ? "link_id" : "id";
                resource.setId((Integer) row.get(columnName));
                break;
            case "urn":
                columnName = isLink ? "link_urn" : "urn";
                var urn = (String) row.get(columnName);
                var urnRecord = Urn.of(urn);
                resource.setUrn(urn);
                resource.setTenantLabel(urnRecord.tenant().isEmpty() ? null : urnRecord.tenant());
                resource.setSchemaLabel(urnRecord.schema());
                break;
            case "label":
                resource.setLabel((String) row.get(columnName));
                break;
            case "pub":
                columnName = isLink ? "link_pub" : "pub";
                resource.setPub((Boolean) row.get(columnName));
                break;
            case "created_at":
                columnName = isLink ? "link_created_at" : "created_at";
                var createdAt = (OffsetDateTime) row.get(columnName);
                resource.setCreatedAt(
                       createdAt != null ? createdAt.toInstant() : null);
                break;
            case "updated_at":
                columnName = isLink ? "link_updated_at" : "updated_at";
                var updatedAt = (OffsetDateTime) row.get(columnName);
                resource.setUpdatedAt(
                        updatedAt != null ? updatedAt.toInstant() : null);
                break;
            case "created_by":
                columnName = isLink ? "link_created_by" : "created_by";
                resource.setCreatedBy((String) row.get(columnName));
                break;
            case "updated_by":
                columnName = isLink ? "link_updated_by" : "updated_by";
                resource.setUpdatedBy((String) row.get(columnName));
                break;

            case "link_id":
                var linkId = (Integer) row.get(columnName);
                if (linkId == null) {
                    resource.setLinkId(null);
                } else {
                    resource.setLinkId((Integer) row.get("id"));
                }
                break;

            case "link_type":
                resource.setLinkType(LinkType.values()[(Integer) row.get(columnName)]);
                break;

            case "tenant_id":
                columnName = isLink ? "link_tenant_id" : "tenant_id";
                var tenantId = (Integer) row.get(columnName);
                if (tenantId == null) {
                    resource.setTenantId(null);
                } /*
                   * else if (tenantId == Constants.TENANT_ROOT_ID) {
                   * resource.setTenant(Ref.builder()
                   * .id(Constants.TENANT_ROOT_ID)
                   * .label(Constants.TENANT_ROOT_LABEL)
                   * .schema(Constants.TENANT_ROOT_SCHEMA_LABEL)
                   * .build());
                   * }
                   */ else {
                    resource.setTenantId(tenantId);
                }

                break;

            case "schema_id":
                var schemaId = (Integer) row.get(columnName);
                if (schemaId == null) {
                    resource.setSchemaId(null);
                } /*
                   * else if (tenantId == Constants.TENANT_ROOT_ID) {
                   * resource.setTenant(Ref.builder()
                   * .id(Constants.TENANT_ROOT_ID)
                   * .label(Constants.TENANT_ROOT_LABEL)
                   * .schema(Constants.TENANT_ROOT_SCHEMA_LABEL)
                   * .build());
                   * }
                   */ else {
                    resource.setSchemaId(schemaId);
                }

                break;
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
                json = (String) row.get(columnName);
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