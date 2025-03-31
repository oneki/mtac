package net.oneki.mtac.test.api.resource.tenant.installer;

import net.oneki.mtac.test.api.resource.tenant.company.CompanyUpsertRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest
@NoArgsConstructor
public class InstallerUpsertRequest extends CompanyUpsertRequest {
}
