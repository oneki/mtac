package net.oneki.mtac.resource.iam.identity.group;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.core.util.R;
import net.oneki.mtac.resource.ResourceService;

public class GroupService<U extends GroupUpsertRequest, E extends Group> extends ResourceService<U, E>{
    @Autowired protected DefaultGroupRefService groupRefService;
    
    public E create(E resourceEntity, Integer groupRefTenantId) {
        var group = super.create(resourceEntity);
        if (groupRefTenantId != null) {
            var groupRef = GroupRef.builder()
            .identity(group)
            .build();
            R.inject(groupRef, groupRefTenantId);
            groupRefService.create(groupRef);
        }
        return group;
    }
}
