package net.oneki.mtac.framework.introspect;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.resource.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ResourceField {
    private Integer id; // id of the field in DB
    private String label; // label of the field. Example: "memberOf"
    private Class<?> fieldClass; // the class of the field. Example: GroupEntity.class
    private Class<?> ownerClass; // the class of the owner of the field. If the field is enforced by an interface, the owner class is the interface
    private Class<? extends Resource> relationClass;
    @Builder.Default private Set<Class<?>> impClasses = new HashSet<>(); // the classes that contain this field
    private String type; // example: iam.identiy.group (primary type are: string, number, boolean,
                         // object)
    private boolean multiple; // true if it's an array
    private Field field; // the field itself
    private Field collectionField; // only present if multiple = true
    private SecretDesc secret; // true if the field is marked as a secret
    private String peer; // the peer of the field. Example: "memberOf" for field "members"

    public boolean isRelation() {
        return ResourceField.isRelation(type);
    }

    public static boolean isRelation(String type) {
        return !(type.equals("string") || type.equals("number") || type.equals("boolean") || type.equals("date")
                || type.equals("object") || type.startsWith("req."));
    }

    public Object getValue(Object object) {
        try {
            getField().setAccessible(true);
            return getField().get(object);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException("ILLEGAL_ACCESS",
                    "Error while accessing field " + label + " in " + object.getClass().getSimpleName(), e);
        }
    }

    public void setValue(Object object, Object value) {
        try {
            getField().setAccessible(true);
            getField().set(object, value);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException("ILLEGAL_ACCESS",
                    "Error while accessing field " + label + " in " + object.getClass().getSimpleName(), e);
        }
    }

    public boolean isSecret() {
        return secret != null;
    }
}
