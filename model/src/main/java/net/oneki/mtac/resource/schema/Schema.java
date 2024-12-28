package net.oneki.mtac.resource.schema;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.core.Constants;
import net.oneki.mtac.core.resource.Ref;
import net.oneki.mtac.core.util.introspect.annotation.Entity;
import net.oneki.mtac.resource.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("schema")
public class Schema extends Resource {
    private String name;

    @Builder.Default
    private List<Ref> parents = new ArrayList<>();

    @Override
    public String labelize() {
        return name;
    }

    public static String asUrn(String schemaLabel) {
        return String.format("urn:%s:%s:%s", Constants.TENANT_ROOT_LABEL, Constants.SCHEMA_SCHEMA_LABEL, schemaLabel);
    }
}
