package net.oneki.mtac.model.core.resource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UidLabel implements HasUid, HasLabel {
    private String uid;
    private String label;

}
