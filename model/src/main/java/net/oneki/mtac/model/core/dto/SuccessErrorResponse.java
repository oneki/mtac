package net.oneki.mtac.model.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SuccessErrorResponse {
  public enum Status {
    Success, Warning, Error
  }

  private Status status;
  private String errorCode;
  private String message;

}
