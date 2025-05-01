package net.oneki.mtac.framework.util.resource;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.StringUtils;

public class UrnUtils {

    public static Urn of(Ref ref) {
        var tenantLabel = ResourceRegistry.getTenantLabel(ref.getTenant());
        var schemaLabel = ResourceRegistry.getSchemaLabel(ref.getSchema());
        return new Urn(tenantLabel, schemaLabel, StringUtils.toTechnicalName(ref.getLabel()));
    }

    public static Urn of(String tenantLabel, String schemaLabel, String label) {
        label = StringUtils.toTechnicalName(label);
        return new Urn(tenantLabel, schemaLabel, label);
    }

    
}
