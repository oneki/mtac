package net.oneki.mtac.resource.iam.identity.group;

import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.model.resource.iam.identity.group.GroupUpsertRequest;
import net.oneki.mtac.resource.ResourceService;

public abstract class GroupService<U extends GroupUpsertRequest, E extends Group> extends ResourceService<U, E>{
    
    public E create(E resourceEntity, Integer groupRefTenantId) {
        var group = super.create(resourceEntity);
        if (groupRefTenantId != null) {
            createLink(group, groupRefTenantId, LinkType.Ref, true);
        }
        return group;
    }
}
