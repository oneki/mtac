package net.oneki.mtac.core.util;

import net.oneki.mtac.core.framework.Urn;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.resource.Resource;

public class R {
    public static <T extends Resource> T inject(T resource, String tenantLabel) {
        if (resource == null) {
            return null;
        }
        resource.setTenantLabel(tenantLabel);
        return fillMeta(resource);
    }

    public static <T extends Resource> T inject(T resource, Integer tenantId) {
        if (resource == null) {
            return null;
        }
        resource.setTenantId(tenantId);
        return fillMeta(resource);
    }

    public static <T extends Resource> T fillMeta(T resource) {
        if (resource == null) {
            return null;
        }

        if (resource.getUrn() != null) {
            var urnRecord = Urn.of(resource.getUrn());
            if (resource.getSchemaLabel() == null) {
                resource.setSchemaLabel(urnRecord.schema());
            }
            if (resource.getTenantLabel() == null) {
                resource.setTenantLabel(urnRecord.tenant());
            }
            if (resource.getLabel() == null) {
                resource.setLabel(urnRecord.label());
            }
        }

        if (resource.getTenantId() == null && resource.getTenantLabel() != null) {
            resource.setTenantId(ResourceRegistry.getTenantId(resource.getTenantLabel()));
        }

        if (resource.getTenantLabel() == null && resource.getTenantId() != null) {
            resource.setTenantLabel(ResourceRegistry.getTenantLabel(resource.getTenantId()));
        }

        if (resource.getSchemaId() == null) {
            if (resource.getSchemaLabel() != null) {
                resource.setSchemaId(ResourceRegistry.getSchemaId(resource.getSchemaLabel()));
            } else {
                resource.setSchemaId(ResourceRegistry.getSchemaId(resource.getClass()));
            }
        }

        if (resource.getSchemaLabel() == null) {
            resource.setSchemaLabel(ResourceRegistry.getSchemaLabel(resource.getSchemaId()));
        }

        if (resource.getLabel() == null) {
            resource.setLabel(resource.labelize());
        }

        if (resource.getUrn() == null) {
            resource.setUrn(new Urn(resource.getTenantLabel(), resource.getSchemaLabel(), resource.getLabel()).toString());
        }

        return resource;
    }
}
