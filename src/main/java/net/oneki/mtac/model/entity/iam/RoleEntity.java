package net.oneki.mtac.model.entity.iam;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.embedded.I18n;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.util.introspect.annotation.Entity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.role")
public class RoleEntity extends ResourceEntity {
    private String name;
    private I18n localizedName;
    @Builder.Default private List<String> schemas = new ArrayList<>();
    @Builder.Default private List<String> actions = new ArrayList<>();
    @Builder.Default private List<String> fields = new ArrayList<>();

    @Override
    public String labelize() {
        return name;
    }

}
