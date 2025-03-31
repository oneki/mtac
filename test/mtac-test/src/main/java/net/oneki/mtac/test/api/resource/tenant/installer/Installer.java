package net.oneki.mtac.test.api.resource.tenant.installer;

import net.oneki.mtac.test.api.resource.tenant.company.Company;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity("tenant.installer")
public class Installer extends Company {
}
