package net.oneki.mtac.resource.iam.identity.user;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.resource.iam.identity.group.Group;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest("req.iam.identity.user:upsert")
@NoArgsConstructor
public class UserUpsertRequest extends BaseUserUpsertRequest<Group> {
    
    @Override
    public Class<? extends Group> getGroupClass() {
        return Group.class;
    }
}
