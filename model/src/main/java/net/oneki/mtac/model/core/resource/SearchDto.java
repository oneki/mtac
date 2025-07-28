package net.oneki.mtac.model.core.resource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchDto {
  private String uid;
  private String label;
  private String schema; // schema label
  private String tenantUid;
  private String tenantLabel;
  private Integer resourceType;
}
