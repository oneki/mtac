package net.oneki.mtac.resource.iam.identity.group;

import net.oneki.mtac.core.util.exception.BusinessException;
import net.oneki.mtac.resource.ResourceService;

public class GroupService<U extends GroupUpsertRequest, E extends Group> extends ResourceService<U, E>{
    
    public E create(E resourceEntity) {
        var group = super.create(resourceEntity);
        var groupRef = GroupRef.builder()
            .identity(group)
            .tenantId(null)
            .build();
        // Verify if the user has the permission to create the resource
        if (!permissionService.hasCreatePermission(resourceEntity.getTenantLabel(),
                resourceEntity.getSchemaLabel())) {
            throw new BusinessException("FORBIDDEN", "Forbidden access");
        }
        return resourceRepository.create(resourceEntity);
    }
}
