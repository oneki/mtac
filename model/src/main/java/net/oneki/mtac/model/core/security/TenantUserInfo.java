package net.oneki.mtac.model.core.security;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TenantUserInfo {
  private String uid;
  private String label;
  @JsonProperty("schema")
  private String schemaLabel;  
  @Builder.Default private Set<String> roles = new HashSet<>(); // List of role UIDs
  @Builder.Default private Set<TenantUserInfo> children = new HashSet<>();
}
