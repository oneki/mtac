package net.oneki.mtac.reactive.test.api.resource.tenant.company;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.ApiRequest;
import net.oneki.mtac.model.resource.UpsertRequest;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUpsertRequest extends UpsertRequest {
    private String name;
    private String adminGroup;
    private String assetAdminGroup;
}
