package net.oneki.mtac.framework.introspect;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceDesc {
    protected Integer id; // ID in DB
    protected String label; // example: "iam.identity.user"
    protected String uniqueInScope;
    @Builder.Default protected Set<ResourceField> fields = new HashSet<>();
    protected boolean isInterface;
    @Builder.Default protected Set<Class<?>> childClasses = new HashSet<>();
    protected ClassType classType;
    protected Class<?> clazz;

    public ResourceField getField(String fieldName) {
        for (ResourceField field : fields) {
            if (field.getLabel().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public Object getValue(Object entity, String fieldName) {
        try {
            return getField(fieldName).getField().get(entity);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException("ILLEGAL_ACCESS", "Error while accessing field " + fieldName + " in " + entity.getClass().getSimpleName(), e);
        }
    }

    public void setFieldOwnerOrAddField(ResourceField field, Class<?> ownerClass) {
        var fieldName = field.getLabel();
        var existingField = getField(fieldName);
        var isPrivateField = false;
        if (existingField == null) {
            fields.add(field);
        } else {
            existingField.setOwnerClass(ownerClass);
            isPrivateField = Modifier.isPrivate(existingField.getField().getModifiers());
        }
        field.getImpClasses().add(getClazz());

        if (!isPrivateField) {
            for (var childClass : childClasses) {
                var childResourceDesc = ResourceRegistry.getResourceDesc(childClass);
                if (childResourceDesc != null) {
                    childResourceDesc.setFieldOwnerOrAddField(field, ownerClass);
                }
            }
        }
    }
}
