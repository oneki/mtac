package net.oneki.mtac.model.resource.iam.identity.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Entity;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;
import net.oneki.mtac.model.resource.iam.identity.Identity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Entity("iam.identity.user")
public class User extends Identity {
    @Secret(type = SecretType.HASHING)
    protected String password;
    @Secret(type = SecretType.ENCRYPTION)
    protected String totpSecret;
    protected String refreshToken;
    protected String resetPasswordToken;
    protected Boolean mfa;
    protected Boolean mfaActive;
    @Secret(type = SecretType.ENCRYPTION)
    protected String verificationCode; // totp secret: format "code:expiresAt(timestamp in millisec)", example
                                       // 12345678:1753378999000
    protected String firstName;
    protected String lastName;

}
