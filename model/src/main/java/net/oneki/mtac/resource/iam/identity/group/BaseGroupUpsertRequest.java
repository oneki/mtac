package net.oneki.mtac.resource.iam.identity.group;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.resource.UpsertRequest;
import net.oneki.mtac.resource.iam.identity.Identity;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class BaseGroupUpsertRequest<G extends Group> extends UpsertRequest {
    private String name;
    private List<G> memberOf;
    protected List<Identity> members;

    public abstract Class<? extends Group> getGroupClass();
}
