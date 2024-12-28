package net.oneki.mtac.core.model.framework;

import net.oneki.mtac.core.framework.Urn;
import net.oneki.mtac.core.resource.Ref;
import net.oneki.mtac.core.util.cache.ResourceRegistry;

public class UrnUtils {

    public static Urn of(Ref ref) {
        var tenantLabel = ResourceRegistry.getTenantLabel(ref.getTenant());
        var schemaLabel = ResourceRegistry.getSchemaLabel(ref.getSchema());
        return new Urn(tenantLabel, schemaLabel, ref.getLabel());
    }
}
