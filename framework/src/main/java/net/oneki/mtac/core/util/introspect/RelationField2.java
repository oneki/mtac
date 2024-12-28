package net.oneki.mtac.core.util.introspect;

import net.oneki.mtac.core.util.introspect.ResourceField;
import net.oneki.mtac.resource.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RelationField2 extends ResourceField{
    private Class<? extends Resource> relationClass;
    
    public boolean isRelation() {
        return true;
    }
}
