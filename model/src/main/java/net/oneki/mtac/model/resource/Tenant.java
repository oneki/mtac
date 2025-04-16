package net.oneki.mtac.model.resource;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity(schema = "tenant")
public class Tenant extends Resource {
    private String name;

    @Override
    public String labelize() {
        return name;
    }

    public static String toLabel(String name, String tenantLabel) {
        return name + "@" + tenantLabel.replace("@", ".");
    }
}
