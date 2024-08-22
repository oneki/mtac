package net.oneki.mtac.util.introspect;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.util.exception.UnexpectedException;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceDesc {
    protected String label; // example: "iam.identity.user"
    protected String uniqueInScope;
    @Builder.Default protected Set<ResourceField> fields = new HashSet<>();
    protected boolean isInterface;

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
}
