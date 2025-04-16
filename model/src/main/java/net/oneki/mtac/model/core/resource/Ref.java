package net.oneki.mtac.model.core.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Ref {
    private Integer id;

    @JsonProperty("$t")
    private Integer tenant;
    @JsonProperty("$l")
    private String label;
    @JsonProperty("$s")
    private Integer schema;

    public Ref(Integer id) {
        this.id = id;
    }

}
