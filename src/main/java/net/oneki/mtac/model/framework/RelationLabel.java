package net.oneki.mtac.model.framework;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.entity.ResourceEntity;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RelationLabel {
    private String label;
    private Class<? extends ResourceEntity> schema;
    private String tenantLabel;
}