package net.oneki.mtac.model.api.iam.identity.user;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.api.UpsertRequest;
import net.oneki.mtac.model.entity.iam.identity.GroupEntity;
import net.oneki.mtac.util.introspect.annotation.ApiRequest;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ApiRequest("req.iam.identity.user:upsert")
public class DefaultUserUpsertRequest extends UpsertRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private List<GroupEntity> memberOf;
}
