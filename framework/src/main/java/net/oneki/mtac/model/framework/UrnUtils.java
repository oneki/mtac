package net.oneki.mtac.model.framework;

import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.util.cache.ResourceRegistry;

public class UrnUtils {

    public static Urn of(Ref ref) {
        var tenantLabel = ResourceRegistry.getTenantLabel(ref.getTenant());
        var schemaLabel = ResourceRegistry.getSchemaLabel(ref.getSchema());
        return new Urn(tenantLabel, schemaLabel, ref.getLabel());
    }
}
