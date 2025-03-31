package net.oneki.mtac.framework.entity;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.core.resource.HasId;
import net.oneki.mtac.model.core.resource.HasLabel;

@Data
@NoArgsConstructor
@Builder
public class Ancestor implements HasLabel, HasId {
	protected Integer id;
	protected String label;
	protected Integer depth;

	public Ancestor(Integer id, String label, Integer depth) {
		this.label = label;
		this.depth = depth;
	}

	public String getName() {
		return getLabel();
	}

	public void setName(String name) {
		setLabel(name);
	}

	@Override
	public boolean equals(Object otherObject) {
		if (otherObject instanceof Ancestor) {
			Ancestor otherTenant = (Ancestor) otherObject;
			if (id != null && id.equals(otherTenant.getId())) {
				return true;
			}

			if (label != null && label.equals(otherTenant.getLabel())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
        if (id != null) return id;
        if (label != null) {
            return label.hashCode();
		}
        if (getName() != null) {
            return getName().hashCode();
        }
        return 0;
	}

}
