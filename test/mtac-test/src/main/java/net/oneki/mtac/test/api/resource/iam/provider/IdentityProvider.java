package net.oneki.mtac.test.api.resource.iam.provider;

import net.oneki.mtac.model.core.util.introspect.annotation.EntityInterface;

@EntityInterface("iam.provider.identity")
public interface IdentityProvider {
    public String getAuthorizationUrl();
}
