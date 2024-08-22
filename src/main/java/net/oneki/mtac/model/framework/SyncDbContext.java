package net.oneki.mtac.model.framework;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.entity.FieldEntity;
import net.oneki.mtac.util.introspect.ResourceDesc;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncDbContext {
    @Builder.Default private List<FieldEntity> fieldEntities = new ArrayList<>();
    @Builder.Default private Map<String, FieldEntity> fieldSchemaIndex = new HashMap<>();
    @Builder.Default private Map<Integer, FieldEntity> fieldIndex = new HashMap<>();
    @Builder.Default private Map<Integer, FieldEntity> nextFieldIndex = new HashMap<>();
    @Builder.Default private Map<String, FieldEntity> nextFieldSchemaIndex = new HashMap<>();
    @Builder.Default private Map<ResourceDesc, List<FieldEntity>> nextFieldEntitiesByResourceDesc = new HashMap<>();
}
