package net.oneki.mtac.model.core.openid;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwksResponse {
    @Singular
    protected List<Map<String, Object>> keys;
}
