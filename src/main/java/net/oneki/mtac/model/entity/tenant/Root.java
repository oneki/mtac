package net.oneki.mtac.model.entity.tenant;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.entity.TenantEntity;
import net.oneki.mtac.util.introspect.annotation.Entity;


@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity("tenant.root")
public class Root extends TenantEntity {
    @Override
    public String labelize() {
        return "root";
    }
}
