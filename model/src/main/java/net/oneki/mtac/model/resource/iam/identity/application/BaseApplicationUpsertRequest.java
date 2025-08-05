package net.oneki.mtac.model.resource.iam.identity.application;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.group.Group;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class BaseApplicationUpsertRequest<G extends Group> extends UpsertRequest {
    private String name;
    @Secret(type = SecretType.HASHING)
    private String password;
    private String description;
    private List<G> memberOf;

    public abstract Class<? extends Group> getGroupClass();
}
