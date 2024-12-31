package net.oneki.mtac.resource.iam.identity.group;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.core.util.R;
import net.oneki.mtac.core.util.exception.UnexpectedException;
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

    @Override
    public void delete(E resourceEntity) {
        var groupRef = groupRefService.getByLabelOrUrn(resourceEntity.getLabel(), GroupRef.class);
        if (groupRef.getIdentity().getId() != resourceEntity.getId()) {
            throw new UnexpectedException("EXC_GROUPREF_DELETE", "There is a group ref with the same label but different id. GroupRef = " + groupRef + ", Group = " + resourceEntity); 
        }

        groupRefService.delete(groupRef);
        super.delete(resourceEntity);
    }
}
