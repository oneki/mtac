package net.oneki.mtac.resource.iam.tenant.root;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.resource.iam.tenant.Tenant;


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
