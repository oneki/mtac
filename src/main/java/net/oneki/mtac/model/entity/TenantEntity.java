package net.oneki.mtac.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.embedded.I18n;
import net.oneki.mtac.util.introspect.annotation.Entity;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity(schema = "tenant")
public class TenantEntity extends ResourceEntity {
    private I18n name;

    @Override
    public String labelize() {
        if (name == null) throw new RuntimeException("TenantEntity must have a name");
        if (name.getEn() == null) throw new RuntimeException("TenantEntity must have an English name");
        return name.getEn();
    }
}
