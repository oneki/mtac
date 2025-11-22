package net.oneki.mtac.model.core.event;

import org.springframework.context.ApplicationEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class ResourceRegistryInitializedEvent extends ApplicationEvent {
  public ResourceRegistryInitializedEvent(Object source) {
    super(source);
  }
}
