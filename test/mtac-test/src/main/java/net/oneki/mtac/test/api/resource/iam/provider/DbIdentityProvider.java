package net.oneki.mtac.test.api.resource.iam.provider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.resource.Resource;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity("iam.provider.identity.db")
public class DbIdentityProvider extends Resource implements IdentityProvider {
    private String name;
    private String authorizationUrl;
    private String driver;

    @Override
    public String labelize() {
        return name;
    }
}
