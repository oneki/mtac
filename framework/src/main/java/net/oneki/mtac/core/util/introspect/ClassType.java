package net.oneki.mtac.core.util.introspect;

public enum ClassType {
    Entity, ApiRequest;

    public static ClassType fromClass(Class<?> clazz) {
        if (clazz.isAnnotationPresent(net.oneki.mtac.core.util.introspect.annotation.Entity.class) ||
            clazz.isAnnotationPresent(net.oneki.mtac.core.util.introspect.annotation.EntityInterface.class)) {
            return Entity;
        } else if (clazz.isAnnotationPresent(net.oneki.mtac.core.util.introspect.annotation.ApiRequest.class) ||
                   clazz.isAnnotationPresent(net.oneki.mtac.core.util.introspect.annotation.ApiRequestInterface.class)) {
            return ApiRequest;
        } else {
            return null;
        }
    }
}