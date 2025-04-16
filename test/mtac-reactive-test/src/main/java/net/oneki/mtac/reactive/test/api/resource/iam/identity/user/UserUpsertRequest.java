package net.oneki.mtac.reactive.test.api.resource.iam.identity.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.model.resource.iam.identity.user.BaseUserUpsertRequest;
import net.oneki.mtac.reactive.test.api.resource.iam.provider.IdentityProvider;


@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest
@NoArgsConstructor
@AllArgsConstructor
public class UserUpsertRequest extends BaseUserUpsertRequest<Group> {
    private String idTag;
    private IdentityProvider idp;
    
    @Override
    public Class<? extends Group> getGroupClass() {
        return Group.class;
    }

}
