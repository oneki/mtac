package net.oneki.mtac.model.core.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.resource.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Ref {
    private Integer id;

    // @JsonProperty("t")
    // private Integer tenant;
    private String label;
    @JsonProperty("s")
    private Integer schema;

    public Ref(Integer id) {
        this.id = id;
    }

    @JsonIgnore
    public String getUid() {
        return id != null ? Resource.toUid(id) : null;
    }

}
