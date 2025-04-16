package net.oneki.mtac.model.core.security;

import java.util.ArrayList;
import java.util.List;

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
  private String urn;
  private String name;
  @Builder.Default private List<TenantHiearchy> hierarchy = new ArrayList<>();

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @SuperBuilder
  public static class TenantHiearchy {
    private String urn;
    private String name;
  }
}
