package net.oneki.mtac.framework.introspect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SecretDesc {
  private SecretType type;
}
