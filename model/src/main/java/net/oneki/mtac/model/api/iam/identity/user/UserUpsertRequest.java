package net.oneki.mtac.model.api.iam.identity.user;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.entity.iam.identity.Group;
import net.oneki.mtac.util.introspect.annotation.ApiRequest;

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
