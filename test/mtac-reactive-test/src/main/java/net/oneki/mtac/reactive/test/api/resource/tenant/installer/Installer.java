package net.oneki.mtac.reactive.test.api.resource.tenant.installer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.reactive.test.api.resource.tenant.company.Company;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity("tenant.installer")
public class Installer extends Company {
}
