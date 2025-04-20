package net.oneki.mtac.model.core.security;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class TenantWithHierarchy {
  @JsonIgnore
  private int id;
  @JsonProperty("$urn")
  private String urn;
  private String name;
  @Builder.Default private List<TenantHierarchy> hierarchy = new ArrayList<>();

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @SuperBuilder
  public static class TenantHierarchy {
    @JsonIgnore
    private int id;
    @JsonProperty("$urn")
    private String urn;
    private String name;
  }
}
