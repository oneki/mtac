package net.oneki.mtac.model.entity;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.config.Constants;
import net.oneki.mtac.model.embedded.I18n;
import net.oneki.mtac.util.introspect.annotation.Entity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("schema")
public class SchemaEntity extends ResourceEntity {
    private I18n name;

    @Builder.Default
    private List<Ref> parents = new ArrayList<>();

    @Override
    public String labelize() {
        if (name == null) throw new RuntimeException("SchemaEntity must have a name");
        if (name.getEn() == null) throw new RuntimeException("SchemaEntity must have an English name");
        return name.getEn();
    }

    public static String asUrn(String schemaLabel) {
        return String.format("%s:%s:%s", Constants.TENANT_ROOT_LABEL, Constants.SCHEMA_SCHEMA_LABEL, schemaLabel);
    }
}
