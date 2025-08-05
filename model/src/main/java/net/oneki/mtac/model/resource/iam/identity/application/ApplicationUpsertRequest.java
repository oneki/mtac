package net.oneki.mtac.model.resource.iam.identity.application;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.model.resource.iam.identity.group.Group;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest("req.mtac.iam.identity.application:upsert")
@NoArgsConstructor
public class ApplicationUpsertRequest extends BaseApplicationUpsertRequest<Group> {
    
    @Override
    public Class<? extends Group> getGroupClass() {
        return Group.class;
    }
}
