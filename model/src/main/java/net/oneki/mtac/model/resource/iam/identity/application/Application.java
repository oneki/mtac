package net.oneki.mtac.model.resource.iam.identity.application;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;
import net.oneki.mtac.model.resource.ResourceType;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.OauthIdentity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.application")
public class Application extends Identity implements OauthIdentity {
    @Secret(type = SecretType.HASHING)
    protected String password;
    protected Integer tokenExpiresIn; // accessToken in seconds. If null, the default value is used (usually 900
                                      // seconds)
    protected String refreshToken;
    protected String name;
    private String description;
    // token endpoint authentication; null = client_secret (see ApplicationAuthMethod.of)
    protected ApplicationAuthMethod tokenEndpointAuthMethod;
    protected String jwks; // private_key_jwt: JWK Set (JSON) of the public keys, or null when jwksUri is used
    protected String jwksUri; // private_key_jwt: https URL of the JWK Set, or null when jwks is used
    @Builder.Default
    protected Integer resourceType = ResourceType.PUBLIC_RESOURCE;
}
