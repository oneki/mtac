package net.oneki.mtac.model.framework;

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
    Integer offset;
    Integer limit;
}
