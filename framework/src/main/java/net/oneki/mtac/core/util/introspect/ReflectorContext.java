package net.oneki.mtac.core.util.introspect;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReflectorContext {
    @Builder.Default private Map<Class<?>, ResourceInterface> interfaceIndex = new HashMap<>();
    private Map<String, Class<?>> classIndex;
    private Map<Class<?>, String> schemaIndex;
    private Map<String, ResourceDesc> resourceDescIndex;
    private String basePackage;
    private ClassType classType;
}
