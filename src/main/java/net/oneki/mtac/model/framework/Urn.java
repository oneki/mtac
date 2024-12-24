package net.oneki.mtac.model.framework;

import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.util.cache.ResourceRegistry;

public record Urn(String tenant, String schema, String label) {

    public static Urn of(String urn) {
        if (urn.startsWith("urn:")) {
            urn = urn.substring(4);
        } else {
            throw new IllegalArgumentException("Invalid URN: " + urn + ". URN must start with 'urn:'");
        }

        try {
            int pos = urn.indexOf(":");
            var tenant = urn.substring(0, pos);
            int pos2 = urn.indexOf(":", pos + 1);
            var schema = urn.substring(pos + 1, pos2);
            var label = urn.substring(pos2 + 1);
            return new Urn(tenant, schema, label);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URN: urn:" + urn);
        }
    }

    public static Urn of(Ref ref) {
        var tenantLabel = ResourceRegistry.getTenantLabel(ref.getTenant());
        var schemaLabel = ResourceRegistry.getSchemaLabel(ref.getSchema());
        return new Urn(tenantLabel, schemaLabel, ref.getLabel());
    }

    public String toString() {
        return "urn:" + tenant + ":" + schema + ":" + label;
    }
}
