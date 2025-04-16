package net.oneki.mtac.test.api.resource.tenant.company;

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
@Entity("tenant.company")
public class Company extends Tenant {
    private String companyNumber;
    private boolean installer;
}
