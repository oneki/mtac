package net.oneki.mtac.resource.iam.identity.group;

import org.springframework.stereotype.Service;

@Service
public class DefaultGroupService extends GroupService<GroupUpsertRequest, Group>{

    @Override
    public Class<Group> getEntityClass() {
        return Group.class;
    }

    @Override
    public Class<GroupUpsertRequest> getRequestClass() {
        return GroupUpsertRequest.class;
    }
}
