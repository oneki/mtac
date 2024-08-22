package net.oneki.mtac.model.entity.iam.identity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.entity.iam.IdentityEntity;
import net.oneki.mtac.util.introspect.annotation.Entity;
import net.oneki.mtac.util.introspect.annotation.Peer;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.group")
public class GroupEntity extends IdentityEntity {
	private String name;
	private String description;

	@Peer("memberOf")
	protected List<IdentityEntity> members;

	@Override
	public String labelize() {
		return name;
	}

}
