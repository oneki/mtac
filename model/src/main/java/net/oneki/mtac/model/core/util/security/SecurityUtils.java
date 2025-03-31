package net.oneki.mtac.model.core.util.security;

import java.util.List;

import net.oneki.mtac.model.core.util.security.SecurityContext;

public class SecurityUtils {
    public static List<Integer> getTenantSids(SecurityContext securityContext) {
        var tenantSids = securityContext.getTenantSids();
        if (tenantSids == null || tenantSids.isEmpty()) {
            tenantSids = List.of(0);
        }

        return tenantSids;
    }
}
