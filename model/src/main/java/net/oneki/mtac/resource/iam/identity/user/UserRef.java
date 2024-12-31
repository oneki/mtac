package net.oneki.mtac.resource.iam.identity.user;

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
@Entity("iam.identity.userRef")
public class UserRef extends IdentityRef {
    protected User identity;
    
    @Override
    public String labelize() {
        return identity.getLabel();
    }
    
}
