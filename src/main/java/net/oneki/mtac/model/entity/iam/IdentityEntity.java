package net.oneki.mtac.model.entity.iam;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.model.entity.iam.identity.GroupEntity;
import net.oneki.mtac.util.introspect.annotation.Entity;
import net.oneki.mtac.util.introspect.annotation.Peer;


@SuperBuilder
@Data
@NoArgsConstructor
@Entity("iam.identity")
@EqualsAndHashCode(callSuper = true)
public class IdentityEntity extends ResourceEntity {
    private String email;

    @Peer("members")
    private List<GroupEntity> memberOf;

    // @Lookup
    // public abstract void labelize();

    @Override
	public String labelize() {
		return"";
	}
}

