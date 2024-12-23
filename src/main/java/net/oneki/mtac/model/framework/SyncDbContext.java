package net.oneki.mtac.model.framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.entity.Field;
import net.oneki.mtac.util.introspect.ResourceDesc;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncDbContext {
    @Builder.Default private List<Field> fieldEntities = new ArrayList<>();
    @Builder.Default private Map<String, Field> fieldSchemaIndex = new HashMap<>();
    @Builder.Default private Map<Integer, Field> fieldIndex = new HashMap<>();
    @Builder.Default private Map<Integer, Field> nextFieldIndex = new HashMap<>();
    @Builder.Default private Map<String, Field> nextFieldSchemaIndex = new HashMap<>();
    @Builder.Default private Map<ResourceDesc, List<Field>> nextFieldEntitiesByResourceDesc = new HashMap<>();
}
