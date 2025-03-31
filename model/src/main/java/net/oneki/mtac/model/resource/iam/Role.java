package net.oneki.mtac.model.resource.iam;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.resource.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.role")
public class Role extends Resource {
    private String name;
    @Builder.Default private List<String> schemas = new ArrayList<>();
    @Builder.Default private List<String> actions = new ArrayList<>();
    @Builder.Default private List<String> fields = new ArrayList<>();

    @Override
    public String labelize() {
        return name;
    }

}
