package net.oneki.mtac.model.resource.iam.identity;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.core.util.introspect.annotation.Peer;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.group.Group;


@SuperBuilder
@Data
@NoArgsConstructor
@Entity("iam.identity")
@EqualsAndHashCode(callSuper = true)
public class Identity extends Resource {
    protected String email;

    @Peer("members")
    protected List<Group> memberOf;

    // @Lookup
    // public abstract void labelize();
}

