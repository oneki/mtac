package net.oneki.mtac.resource.iam.identity;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.core.util.introspect.annotation.Peer;
import net.oneki.mtac.resource.Resource;
import net.oneki.mtac.resource.iam.identity.group.Group;


@SuperBuilder
@Data
@NoArgsConstructor
@Entity("iam.identity")
@EqualsAndHashCode(callSuper = true)
public class Identity extends Resource {
    private String email;

    @Peer("members")
    private List<Group> memberOf;

    // @Lookup
    // public abstract void labelize();

    @Override
	public String labelize() {
		return"";
	}
}

