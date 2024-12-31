package net.oneki.mtac.resource.iam.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.resource.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identityRef")
public class IdentityRef extends Resource{
    protected Identity identity;
    
    @Override
    public String labelize() {
        return identity.getLabel();
    }
    
}
