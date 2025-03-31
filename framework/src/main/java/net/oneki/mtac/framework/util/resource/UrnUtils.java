package net.oneki.mtac.framework.util.resource;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.resource.Ref;

public class UrnUtils {

    public static Urn of(Ref ref) {
        var tenantLabel = ResourceRegistry.getTenantLabel(ref.getTenant());
        var schemaLabel = ResourceRegistry.getSchemaLabel(ref.getSchema());
        return new Urn(tenantLabel, schemaLabel, ref.getLabel());
    }
}
