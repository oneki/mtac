package net.oneki.mtac.model.resource.tenant;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.resource.Tenant;


@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity("tenant.root")
public class Root extends Tenant {
    @Override
    public String labelize() {
        return "root";
    }
}
