package net.oneki.mtac.model.core.security;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.resource.iam.Role;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class TenantRole {
  @Builder.Default private Set<Role> roles = new HashSet<>();
  private TenantWithHierarchy tenant;
}
