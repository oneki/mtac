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
  private String uid;
  private String label;
  @JsonProperty("schema")
  private String schemaLabel;  
  @Builder.Default private List<TenantHierarchy> hierarchy = new ArrayList<>();

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @SuperBuilder
  public static class TenantHierarchy {
    @JsonIgnore
    private int id;
    private String uid;
    private String label;
    @JsonProperty("schema")
    private String schemaLabel;
  }
}
