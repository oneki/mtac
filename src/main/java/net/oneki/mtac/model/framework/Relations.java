package net.oneki.mtac.model.framework;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.oneki.mtac.model.framework.RelationLabel;
import net.oneki.mtac.model.framework.Relations;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.util.query.Relation;


public class Relations {
    private Map<Integer, ResourceEntity> byId = new HashMap<>();
    private Map<UUID, ResourceEntity> byPublicId = new HashMap<>();
    private Map<Class<? extends ResourceEntity>, Map<String, Map<String, ResourceEntity>>> byLabel = new HashMap<>(); // key = schemaLabel, value = Map<tenantLabel, Map<resourceLabel, ResourceEntity>>

    public Map<Integer, ResourceEntity> getRelationsById() {
        return byId;
    }

    public Map<UUID, ResourceEntity> getRelationsByPublicId() {
        return byPublicId;
    }

    public Map<Class<? extends ResourceEntity>, Map<String, Map<String, ResourceEntity>>> getRelationsByLabel() {
        return byLabel;
    }

    public void putId(Integer id, ResourceEntity resource) {
        byId.put(id, resource);
    }

    public void putPublicId(UUID publicId, ResourceEntity resource) {
        byPublicId.put(publicId, resource);
    }

    public void putLabel(RelationLabel label, ResourceEntity resource) {
        Map<String, Map<String, ResourceEntity>> schemaResourceEntitys = byLabel.getOrDefault(label.getSchema(), new HashMap<String, Map<String, ResourceEntity>>());
        Map<String, ResourceEntity> tenantResourceEntitys = schemaResourceEntitys.getOrDefault(label.getTenantLabel(), new HashMap<String, ResourceEntity>());
        tenantResourceEntitys.put(label.getLabel(), resource);
        schemaResourceEntitys.put(label.getTenantLabel(), tenantResourceEntitys);
        byLabel.put(label.getSchema(), schemaResourceEntitys);
    }

    public ResourceEntity getResourceEntityById(Integer id) {
        return byId.get(id);
    }

    public ResourceEntity getResourceEntityByPublicId(UUID publicId) {
        return byPublicId.get(publicId);
    }

    public ResourceEntity getResourceEntityByLabel(RelationLabel label) {
        Map<String, Map<String, ResourceEntity>> schemaResourceEntitys = byLabel.getOrDefault(label.getSchema(), null);
        if (schemaResourceEntitys == null) {
            return null;
        }
        Map<String, ResourceEntity> tenantResourceEntitys = schemaResourceEntitys.getOrDefault(label.getTenantLabel(), null);
        if (tenantResourceEntitys == null) {
            return null;
        }
        return tenantResourceEntitys.get(label.getLabel());
    }

    public void add(Relations relations) {
        byId.putAll(relations.getRelationsById());
        byPublicId.putAll(relations.getRelationsByPublicId());
        for (var schema: relations.getRelationsByLabel().keySet()) {
            Map<String, Map<String, ResourceEntity>> schemaResourceEntitys = byLabel.getOrDefault(schema, new HashMap<String, Map<String, ResourceEntity>>());
            for (String tenant: relations.getRelationsByLabel().get(schema).keySet()) {
                Map<String, ResourceEntity> tenantResourceEntitys = schemaResourceEntitys.getOrDefault(tenant, new HashMap<String, ResourceEntity>());
                tenantResourceEntitys.putAll(relations.getRelationsByLabel().get(schema).get(tenant));
                schemaResourceEntitys.put(tenant, tenantResourceEntitys);
            }
            byLabel.put(schema, schemaResourceEntitys);
        }
    }

    public int size() {
        int result = byId.size();
        result += byPublicId.size();
        for (var schema: byLabel.keySet()) {
            for (String tenant: byLabel.get(schema).keySet()) {
                result += byLabel.get(schema).get(tenant).size();
            }
        }
        return result;
    }

}
