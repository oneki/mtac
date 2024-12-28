package net.oneki.mtac.core.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class I18n {
    private String fr;
    private String nl;
    private String en;
    private String de;
}
