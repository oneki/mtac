package net.oneki.mtac.resource.iam.identity.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.resource.iam.identity.IdentityRef;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("mtac.iam.identity.groupRef")
public class GroupRef extends IdentityRef {
    protected Group identity;
    
    @Override
    public String labelize() {
        return identity.getLabel();
    }
    
}
