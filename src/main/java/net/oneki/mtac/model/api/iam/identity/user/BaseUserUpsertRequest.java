package net.oneki.mtac.model.api.iam.identity.user;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.api.UpsertRequest;
import net.oneki.mtac.model.entity.iam.identity.Group;

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
