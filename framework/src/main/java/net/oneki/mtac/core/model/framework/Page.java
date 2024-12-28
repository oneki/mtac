package net.oneki.mtac.core.model.framework;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Page<T> {
    List<T> data;
    boolean hasNext;
    int offset;
    int limit;
}
