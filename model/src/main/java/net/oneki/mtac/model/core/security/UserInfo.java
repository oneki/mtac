package net.oneki.mtac.model.core.security;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserInfo {
  protected String email;
  protected String firstName;
  protected String lastName;
  @Builder.Default
  protected Set<TenantRole> roles = new HashSet<>();
}
