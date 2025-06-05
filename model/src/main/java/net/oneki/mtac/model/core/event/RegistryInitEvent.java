package net.oneki.mtac.model.core.event;

import org.springframework.context.ApplicationEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class RegistryInitEvent extends ApplicationEvent {
  public RegistryInitEvent(Object source) {
    super(source);
  }
}
