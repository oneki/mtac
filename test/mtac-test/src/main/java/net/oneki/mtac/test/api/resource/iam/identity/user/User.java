package net.oneki.mtac.test.api.resource.iam.identity.user;


import net.oneki.mtac.test.api.resource.iam.provider.IdentityProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.user")
public class User extends net.oneki.mtac.model.resource.iam.identity.user.User {
    private String idTag;
    private IdentityProvider idp;
}
