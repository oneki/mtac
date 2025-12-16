package net.oneki.mtac.framework.json;

import java.util.List;

import org.springframework.stereotype.Component;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;


@Component
public class JsonEntityMapper extends BaseJsonEntityMapper {
    public JsonEntityMapper() {
        super();
        this.mapper = this.mapper.rebuild().addModule(new EntityModule(mapper)).build();
    }

    @Override
    public <T> T postDeserialize(T obj) {
        if (obj == null) {
            return null;
        }
        var resourceDesc = ResourceRegistry.getResourceDesc(obj.getClass());
        if (resourceDesc == null) {
            return obj;
        }
        for (var field : resourceDesc.getFieldRelationIndex()) {
            var fieldValue = field.getValue(obj);
            if (fieldValue == null) {
                continue;
            }
            if (fieldValue instanceof net.oneki.mtac.model.resource.Resource) {
                fillLabels((net.oneki.mtac.model.resource.Resource) fieldValue);
            } else if (fieldValue instanceof List<?>) {
                var list = (List<?>) fieldValue;
                for (var item : list) {
                    if (item instanceof net.oneki.mtac.model.resource.Resource) {
                        fillLabels((net.oneki.mtac.model.resource.Resource) item);
                    }
                }
            }
        }

        for (var field : resourceDesc.getFieldSecretIndex()) {
            var fieldValue = field.getValue(obj);
            if (fieldValue == null || field.getSecret().getType() == SecretType.HASHING) {
                continue;
            }
            // decrypt password
            if (fieldValue instanceof String) {
                var password = PasswordUtil.decrypt((String) fieldValue);
                field.getField().setAccessible(true);
                try {
                    field.getField().set(obj, password);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } else if (fieldValue instanceof List<?>){
                var list = (List<?>) fieldValue;
                for (var item : list) {
                    if (item instanceof String) {
                        var password = PasswordUtil.decrypt((String) item);
                        field.getField().setAccessible(true);
                        try {
                            field.getField().set(obj, password);
                        } catch (IllegalArgumentException | IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        return obj;
    }

    private net.oneki.mtac.model.resource.Resource fillLabels(net.oneki.mtac.model.resource.Resource resource) {
        resource.setSchemaLabel(ResourceRegistry.getSchemaLabel(resource.getSchemaId()));
        if (resource.getTenantId() != null) {
            resource.setTenantLabel(ResourceRegistry.getTenantLabel(resource.getTenantId()));
        }
        return resource;
    }
}
