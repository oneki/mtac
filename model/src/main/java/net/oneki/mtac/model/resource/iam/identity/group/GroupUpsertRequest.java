package net.oneki.mtac.model.resource.iam.identity.group;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest("req.mtac.iam.identity.group:upsert")
@NoArgsConstructor
public class GroupUpsertRequest extends BaseGroupUpsertRequest<Group> {
    
    @Override
    public Class<? extends Group> getGroupClass() {
        return Group.class;
    }
}
