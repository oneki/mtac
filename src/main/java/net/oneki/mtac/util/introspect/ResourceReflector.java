package net.oneki.mtac.util.introspect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.introspect.annotation.Peer;
import net.oneki.mtac.util.introspect.annotation.Secret;

public class ResourceReflector {

    public static ResourceDesc reflect(Class<?> clazz, Map<String, Object> annotAttributeMap) {
        var resourceDesc = new HashSet<ResourceField>();
        List<Field> fields = new ArrayList<Field>();
        fields = getAllFields(fields, clazz);
        for (Field field : fields) {
            if (!isStaticField(field)) {
                resourceDesc.add(getFieldType(field));
            }

        }
        return ResourceDesc.builder()
                .label(ResourceRegistry.getSchemaByClass(clazz))
                .fields(resourceDesc)
                .uniqueInScope((String) annotAttributeMap.get("uniqueInScope"))
                .isInterface(clazz.isInterface())
                .build();
    }



    public static List<Field> getAllFields(List<Field> fields, Class<?> type) {
        fields.addAll(Arrays.asList(type.getDeclaredFields()));

        if (type.getSuperclass() != null) {
            getAllFields(fields, type.getSuperclass());
        }

        return fields;
    }

    private static boolean isStaticField(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    @SuppressWarnings("unchecked")
    private static ResourceField getFieldType(Field field) {
        var isMultiple = false;

        var classType = field.getType();

        if (field.getType().isArray()) {
            isMultiple = true;
            classType = classType.getComponentType();
        } else if (classType.isAssignableFrom(List.class) || classType.isAssignableFrom(Set.class)) {
            isMultiple = true;
            classType = getGenericType(field.getGenericType());
        }

        var type = getType(classType);
        var peer = field.isAnnotationPresent(Peer.class) ? field.getAnnotation(Peer.class).value() : null;
        if (ResourceField.isRelation(type)) {
            return RelationField.builder()
                    .label(field.getName())
                    .type(type)
                    .multiple(isMultiple)
                    .field(field)
                    .fieldClass(classType)
                    .relationClass((Class<? extends ResourceEntity>) classType)
                    .secret(field.isAnnotationPresent(Secret.class))
                    .peer(peer)
                    .build();
        }

        return ResourceField.builder()
                .label(field.getName())
                .type(type)
                .multiple(isMultiple)
                .field(field)
                .fieldClass(classType)
                .secret(field.isAnnotationPresent(Secret.class))
                .peer(peer)
                .build();
    }

    private static Class<?> getGenericType(Type type) {
        var pType = ((ParameterizedType) type).getActualTypeArguments()[0];
        if (pType instanceof Class) {
            return (Class<?>) pType;
        } else {
            return getGenericType(pType);
        }
    }

    public static String getType(Class<?> clazz) {
        if (clazz.isAssignableFrom(String.class)) {
            return "string";
        }
        if (clazz.isAssignableFrom(Integer.class) ||
                clazz.isAssignableFrom(Long.class) ||
                clazz.isAssignableFrom(Double.class) ||
                clazz.isAssignableFrom(Float.class) ||
                clazz.isAssignableFrom(Short.class) ||
                clazz.isAssignableFrom(Byte.class) ||
                clazz.isAssignableFrom(Character.class) ||
                clazz.isAssignableFrom(int.class) ||
                clazz.isAssignableFrom(long.class) ||
                clazz.isAssignableFrom(double.class) ||
                clazz.isAssignableFrom(float.class) ||
                clazz.isAssignableFrom(short.class) ||
                clazz.isAssignableFrom(byte.class) ||
                clazz.isAssignableFrom(char.class)) {
            return "number";
        }
        if (clazz.isAssignableFrom(Boolean.class)) {
            return "boolean";
        }
        if (clazz.isAssignableFrom(Date.class)) {
            return "date";
        }
        var type = ResourceRegistry.getSchemaByClass(clazz);
        if (type == null) return "object";
        return type;
    }



}
