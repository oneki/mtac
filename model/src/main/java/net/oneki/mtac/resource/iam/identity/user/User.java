package net.oneki.mtac.resource.iam.identity.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.core.util.introspect.annotation.Secret;
import net.oneki.mtac.resource.iam.identity.Identity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.user")
public class User extends Identity {
    @Secret private String password;
    private String firstName;
    private String lastName;

    @Override
    public String labelize() {
        return getEmail();
    }


}
