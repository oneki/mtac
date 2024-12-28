package net.oneki.mtac.core.model.framework;

import java.util.HashMap;
import java.util.Map;

import net.oneki.mtac.resource.Resource;


public class Relations {
    private Map<Integer, Resource> byId = new HashMap<>();
    private Map<String, Resource> byUrn = new HashMap<>();
    private Map<Class<? extends Resource>, Map<String, Map<String, Resource>>> byLabel = new HashMap<>(); // key = schemaLabel, value = Map<tenantLabel, Map<resourceLabel, ResourceEntity>>

    public Map<Integer, Resource> getRelationsById() {
        return byId;
    }

    public Map<String, Resource> getRelationsByUrn() {
        return byUrn;
    }

    public Map<Class<? extends Resource>, Map<String, Map<String, Resource>>> getRelationsByLabel() {
        return byLabel;
    }

    public void putId(Integer id, Resource resource) {
        byId.put(id, resource);
    }

    public void putUrn(String urn, Resource resource) {
        byUrn.put(urn, resource);
    }

    public void putLabel(RelationLabel label, Resource resource) {
        Map<String, Map<String, Resource>> schemaResourceEntitys = byLabel.getOrDefault(label.getSchema(), new HashMap<String, Map<String, Resource>>());
        Map<String, Resource> tenantResourceEntitys = schemaResourceEntitys.getOrDefault(label.getTenantLabel(), new HashMap<String, Resource>());
        tenantResourceEntitys.put(label.getLabel(), resource);
        schemaResourceEntitys.put(label.getTenantLabel(), tenantResourceEntitys);
        byLabel.put(label.getSchema(), schemaResourceEntitys);
    }

    public Resource getResourceEntityById(Integer id) {
        return byId.get(id);
    }

    public Resource getResourceEntityByUrn(String urn) {
        return byUrn.get(urn);
    }

    public Resource getResourceEntityByLabel(RelationLabel label) {
        Map<String, Map<String, Resource>> schemaResourceEntitys = byLabel.getOrDefault(label.getSchema(), null);
        if (schemaResourceEntitys == null) {
            return null;
        }
        Map<String, Resource> tenantResourceEntitys = schemaResourceEntitys.getOrDefault(label.getTenantLabel(), null);
        if (tenantResourceEntitys == null) {
            return null;
        }
        return tenantResourceEntitys.get(label.getLabel());
    }

    public void add(Relations relations) {
        byId.putAll(relations.getRelationsById());
        byUrn.putAll(relations.getRelationsByUrn());
        for (var schema: relations.getRelationsByLabel().keySet()) {
            Map<String, Map<String, Resource>> schemaResourceEntitys = byLabel.getOrDefault(schema, new HashMap<String, Map<String, Resource>>());
            for (String tenant: relations.getRelationsByLabel().get(schema).keySet()) {
                Map<String, Resource> tenantResourceEntitys = schemaResourceEntitys.getOrDefault(tenant, new HashMap<String, Resource>());
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
