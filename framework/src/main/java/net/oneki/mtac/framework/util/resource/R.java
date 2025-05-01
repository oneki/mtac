package net.oneki.mtac.framework.util.resource;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.resource.Resource;

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

        return resource;
    }
}
