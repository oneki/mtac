package net.oneki.mtac.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

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

    @JsonAlias("@tenant")
    private String tenant;
    @JsonAlias("@label")
    private String label;
    @JsonAlias("@schema")
    private String schema;

    public Ref(Integer id) {
        this.id = id;
    }

}
