package net.oneki.mtac.model.entity.iam.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.entity.iam.IdentityEntity;
import net.oneki.mtac.util.introspect.annotation.Entity;
import net.oneki.mtac.util.introspect.annotation.Secret;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.user")
public class DefaultUserEntity extends IdentityEntity {
    @Secret private String password;
    private String firstName;
    private String lastName;

    @Override
    public String labelize() {
        return getEmail();
    }


}
