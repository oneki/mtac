package net.oneki.mtac.model.framework;

import java.util.HashMap;
import java.util.Map;

import net.oneki.mtac.model.entity.ResourceEntity;


public class Relations {
    private Map<Integer, ResourceEntity> byId = new HashMap<>();
    private Map<String, ResourceEntity> byUrn = new HashMap<>();
    private Map<Class<? extends ResourceEntity>, Map<String, Map<String, ResourceEntity>>> byLabel = new HashMap<>(); // key = schemaLabel, value = Map<tenantLabel, Map<resourceLabel, ResourceEntity>>

    public Map<Integer, ResourceEntity> getRelationsById() {
        return byId;
    }

    public Map<String, ResourceEntity> getRelationsByUrn() {
        return byUrn;
    }

    public Map<Class<? extends ResourceEntity>, Map<String, Map<String, ResourceEntity>>> getRelationsByLabel() {
        return byLabel;
    }

    public void putId(Integer id, ResourceEntity resource) {
        byId.put(id, resource);
    }

    public void putUrn(String urn, ResourceEntity resource) {
        byUrn.put(urn, resource);
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

    public ResourceEntity getResourceEntityByUrn(String urn) {
        return byUrn.get(urn);
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
        byUrn.putAll(relations.getRelationsByUrn());
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
        result += byUrn.size();
        for (var schema: byLabel.keySet()) {
            for (String tenant: byLabel.get(schema).keySet()) {
                result += byLabel.get(schema).get(tenant).size();
            }
        }
        return result;
    }

}
