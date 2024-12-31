package net.oneki.mtac.resource.iam.identity.group;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.core.util.introspect.annotation.Peer;
import net.oneki.mtac.resource.iam.identity.Identity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.group")
public class Group extends Identity {
	private String name;
	private String description;

	@Peer("memberOf")
	protected List<Identity> members;

	@Override
	public String labelize() {
		return name;
	}

}
