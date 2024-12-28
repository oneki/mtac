package net.oneki.mtac.resource.iam.identity.user;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.resource.UpsertRequest;
import net.oneki.mtac.resource.iam.identity.group.Group;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class BaseUserUpsertRequest<G extends Group> extends UpsertRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private List<G> memberOf;


    public abstract Class<? extends Group> getGroupClass();
}
