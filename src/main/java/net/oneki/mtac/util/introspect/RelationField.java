package net.oneki.mtac.util.introspect;

import net.oneki.mtac.util.introspect.ResourceField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.entity.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RelationField extends ResourceField{
    private Class<? extends Resource> relationClass;
    
    public boolean isRelation() {
        return true;
    }
}
