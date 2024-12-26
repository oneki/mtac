package net.oneki.mtac.util.introspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.oneki.mtac.model.entity.Resource;
import net.oneki.mtac.util.StringUtils;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.exception.UnexpectedException;
import net.oneki.mtac.util.introspect.annotation.ApiRequestInterface;
import net.oneki.mtac.util.introspect.annotation.Entity;
import net.oneki.mtac.util.introspect.annotation.EntityInterface;
import net.oneki.mtac.util.introspect.annotation.Peer;
import net.oneki.mtac.util.introspect.annotation.Secret;

public class ResourceReflector {

    public static interface SuperInterface { // parent, fifi
        public String getFirstName();
    }

    public static interface TestInterface extends SuperInterface { // fifi
        public String getName();
    }

    public static class Parent implements SuperInterface {
        private String firstname;
        public String getFirstName() {
            return "John";
        }
    }

    public static class Fifi extends Parent implements TestInterface {
        private String name;
        public String getName() {
            return "Fifi";
        }
    }

    public static void main(String[] args) {
        var clazz = TestInterface.class;
        var methods = clazz.getDeclaredMethods();
        var clazz2 = SuperInterface.class;
        var methods2 = clazz.getDeclaredMethods();
        var clazz3 = Fifi.class;
        var intf = clazz3.getInterfaces();
        var intf2 = clazz3.getAnnotatedInterfaces();
        var parentClass = clazz3.getSuperclass();

        System.out.println(methods.length);
    }

    public static void reflect(Class<?> clazz, ReflectorContext context) {
        reflect(clazz, context, new HashSet<>());
    }

    public static void reflect(Class<?> clazz, ReflectorContext context, Set<Class<?>> descendantClasses) {
        var schemaLabel = context.getSchemaIndex().get(clazz);
        if (schemaLabel == null) { // if clazz = Resource.class
            return;
        }
        ResourceDesc resourceDesc = context.getResourceDescIndex().get(schemaLabel);
        var superClass = clazz.getSuperclass();
        var parentDescendantClasses = new HashSet<>(descendantClasses);
        parentDescendantClasses.add(clazz);
        if (superClass != null && !superClass.getName().equals("java.lang.Object")) {
            reflect(superClass, context, parentDescendantClasses);
        }
        var resourceFields = new HashSet<ResourceField>();
        var entityAnnotation = clazz.getAnnotation(Entity.class);

        var interfaces = clazz.getInterfaces();
        for (var interfaceClass : interfaces) {
            reflectInterface(interfaceClass, context, parentDescendantClasses);
        }

        if (resourceDesc != null) {
            resourceDesc.getChildClasses().addAll(descendantClasses);
            return;
        }

        List<Field> fields = new ArrayList<Field>();
        fields = getAllFields(fields, clazz);
        for (Field field : fields) {
            if (!isStaticField(field)) {
                resourceFields.add(getFieldType(field));
            }
        }
        var result = ResourceDesc.builder()
                .label(ResourceRegistry.getSchemaByClass(clazz))
                .fields(resourceFields)
                .uniqueInScope(entityAnnotation != null ? entityAnnotation.uniqueInScope() : null)
                .isInterface(clazz.isInterface())
                .classType(ClassType.fromClass(clazz))
                .clazz(clazz)
                .childClasses(descendantClasses)
                .build();
        
        context.getResourceDescIndex().put(schemaLabel, result);
    }

    // private static Set<ResourceDesc> reflectInterfaces(Class<?> clazz, ReflectorContext context, Set<Class<?>> descendantClasses) {
    //     Set<ResourceDesc> resourceDescs = new HashSet<>();
    //     descendantClasses.add(clazz);
    //     var interfaces = clazz.getInterfaces();
    //     for (var interfaceClass : interfaces) {
    //         var interfaceDescendantClasses = new HashSet<>(descendantClasses);
    //         resourceDescs.addAll(reflectInterfaces(interfaceClass, context, interfaceDescendantClasses));
    //     }
    //     var resourceDesc = reflectInterface(clazz, context);
    //     if (resourceDesc != null) {
    //         resourceDesc.getChildClasses().addAll(descendantClasses);
    //         resourceDescs.add(resourceDesc);
    //     }
    //     return resourceDescs;
    // }

    private static void reflectInterface(Class<?> clazz, ReflectorContext context, Set<Class<?>> descendantClasses) {
        String schemaLabel = null;
        
        var annotation = clazz.getAnnotation(EntityInterface.class);
        if (annotation == null) return;
        if (!annotation.value().isEmpty()) {
            schemaLabel = annotation.value();
        } else {
            schemaLabel = buildSchemaLabel(clazz, context);
        } 
        ResourceDesc resourceDesc = context.getResourceDescIndex().get(schemaLabel);

        var interfaces = clazz.getInterfaces();
        var interfaceDescendantClasses = new HashSet<>(descendantClasses);
        interfaceDescendantClasses.add(clazz);
        for (var interfaceClass : interfaces) {
            reflectInterface(interfaceClass, context, interfaceDescendantClasses);
        }

        if (resourceDesc != null) {
            resourceDesc.getChildClasses().addAll(descendantClasses);
            return;
        }

        var resourceFields = new HashSet<ResourceField>();
        var methods = clazz.getDeclaredMethods();
        for (var method : methods) {
            if (method.getName().startsWith("get")) {
                var fieldName = method.getName().substring(3);
                fieldName = StringUtils.pascalToCamel(fieldName);
                var returnType = method.getReturnType();
                var isMultiple = false;
                if (returnType.isArray()) {
                    isMultiple = true;
                    returnType = returnType.getComponentType();
                } else if (returnType.isAssignableFrom(List.class) || returnType.isAssignableFrom(Set.class)) {
                    isMultiple = true;
                    returnType = getGenericType(method.getGenericReturnType());
                }
                resourceFields.add(ResourceField.builder()
                        .label(fieldName)
                        .type(getType(method.getReturnType()))
                        .multiple(isMultiple)
                        .field(null)
                        .fieldClass(returnType)
                        .ownerClass(clazz)
                        .secret(method.isAnnotationPresent(Secret.class))
                        .peer(method.isAnnotationPresent(Peer.class) ? method.getAnnotation(Peer.class).value() : null)
                        .build());
            }
        }
        
        resourceDesc = ResourceDesc.builder()
                .label(schemaLabel)
                .fields(resourceFields)
                .isInterface(true)
                .clazz(clazz)
                .classType(ClassType.fromClass(clazz))
                .childClasses(descendantClasses)
                .build();
        context.getResourceDescIndex().put(schemaLabel, resourceDesc);
        addResourceClass(schemaLabel, clazz, context);
    }

    public static String buildSchemaLabel(Class<?> clazz, ReflectorContext context) {
        var schemaLabel = "";
        var className = clazz.getName();
        if (context.getClassType() == ClassType.Entity || clazz.isInterface()) {
            schemaLabel = className.substring(context.getBasePackage().length() + 1);
            var substrLength = 0;
            if (schemaLabel.endsWith("Entity")) {
                substrLength = 6;
            }
            var tokens = schemaLabel.split("\\.");
            tokens[tokens.length - 1] = StringUtils.pascalToCamel(
                    tokens[tokens.length - 1].substring(0, tokens[tokens.length - 1].length() - substrLength));
            schemaLabel = String.join(".", tokens);
            schemaLabel = StringUtils.pascalToSnake(schemaLabel);
        } else if (context.getClassType() == ClassType.ApiRequest) {
            var tokens = className.split("\\.");
            var dtoName = tokens[tokens.length - 2]; // e.g: "user"
            var requestType = tokens[tokens.length - 1]; // e.g: "UserCreateRequest"
            if (!requestType.endsWith("Request")) {
                throw new UnexpectedException("INVALID_ANNOTATION", "The class " + clazz
                        + " annoted with @ApiRequest must end with 'Request'.");
            }
            var action = requestType.substring(dtoName.length(), requestType.length() - 7); // e.g: "Create"

            schemaLabel = className.substring(context.getBasePackage().length() + 1,
            className.lastIndexOf("."));
            schemaLabel = "req." + schemaLabel + ":" + StringUtils.pascalToCamel(action);
            schemaLabel = StringUtils.pascalToSnake(schemaLabel);
        }

        return schemaLabel;
    }

    private static void addResourceClass(String schemaLabel, Class<?> resourceClass, ReflectorContext context) {
        context.getClassIndex().put(schemaLabel, resourceClass);
        context.getSchemaIndex().put(resourceClass, schemaLabel);
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
                    .ownerClass(field.getDeclaringClass())
                    .relationClass((Class<? extends Resource>) classType)
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
                .ownerClass(field.getDeclaringClass())
                .secret(field.isAnnotationPresent(Secret.class))
                .peer(peer)
                .build();
    }

    private static Class<?> getGenericType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof TypeVariable) {
            return getGenericType(((TypeVariable<?>) type).getBounds()[0]);
        }
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
        if (type == null)
            return "object";
        return type;
    }

}
