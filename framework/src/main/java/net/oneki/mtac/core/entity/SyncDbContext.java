package net.oneki.mtac.core.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.core.util.introspect.ResourceDesc;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncDbContext {
    @Builder.Default private List<Field> fieldEntities = new ArrayList<>();
    @Builder.Default private Map<String, Field> dbFieldSchemaIndex = new HashMap<>();
    @Builder.Default private Map<Integer, Field> dbFieldIndex = new HashMap<>(); // fields already in the database
    @Builder.Default private Map<Integer, Field> scannedFieldIndex = new HashMap<>(); // fields scanned in classes
    @Builder.Default private Map<String, Field> scannedFieldSchemaIndex = new HashMap<>(); 
    @Builder.Default private Map<ResourceDesc, List<Field>> scannedFieldEntitiesByResourceDesc = new HashMap<>();
}
