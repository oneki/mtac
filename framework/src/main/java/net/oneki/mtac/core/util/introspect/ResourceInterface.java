package net.oneki.mtac.core.util.introspect;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceInterface {
    @Builder.Default
    private Set<Class<?>> impClasses = new HashSet<>();
    private ResourceInterfaceField field;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ResourceInterfaceField {
        private String name;
        private Method method;
    }

}
