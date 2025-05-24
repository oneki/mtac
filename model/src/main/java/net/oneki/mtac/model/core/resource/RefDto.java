package net.oneki.mtac.model.core.resource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefDto {
    private String uid;

    // @JsonProperty("t")
    // private Integer tenant;
    private String label;
    private String schema;

}
