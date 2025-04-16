package net.oneki.mtac.reactive.test.api.resource.iam.identity.user;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.reactive.test.api.resource.iam.provider.IdentityProvider;

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
