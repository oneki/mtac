package net.oneki.mtac.resource;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
// @AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class UpsertRequest extends ResourceRequest {
    // private String label;
    // private String schema;
    private String tenant;

    // public final String getLabel() {
    //     return label;
    // }

    // public	final String getSchema() {
    //     return schema;
    // }

    public final String getTenant() {
        return tenant;
    }

    // public final void setLabel(String label) {
    //     this.label = label;
    // }

    // public final void setSchema(String schema) {
    //     this.schema = schema;
    // }

    public final void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
