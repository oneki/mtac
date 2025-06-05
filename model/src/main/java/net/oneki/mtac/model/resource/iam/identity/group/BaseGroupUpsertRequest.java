package net.oneki.mtac.model.resource.iam.identity.group;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.Identity;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class BaseGroupUpsertRequest<G extends Group> extends UpsertRequest {
    protected String description;
    protected List<G> memberOf;
    protected List<Identity> members;


    public abstract Class<? extends Group> getGroupClass();
}
