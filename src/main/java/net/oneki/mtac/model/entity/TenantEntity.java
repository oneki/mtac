package net.oneki.mtac.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.util.introspect.annotation.Entity;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity(schema = "tenant")
public class TenantEntity extends ResourceEntity {
    private String name;

    @Override
    public String labelize() {
        return name;
    }
}
