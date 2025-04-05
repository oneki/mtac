package net.oneki.mtac.resource.iam.identity.group;

import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.model.resource.iam.identity.group.GroupUpsertRequest;
import net.oneki.mtac.resource.ResourceService;
import reactor.core.publisher.Mono;

public abstract class GroupService<U extends GroupUpsertRequest, E extends Group> extends ResourceService<U, E>{
    
    public Mono<E> create(E resourceEntity, Integer groupRefTenantId) {
        return super.create(resourceEntity)
            .flatMap(group -> {
                if (groupRefTenantId != null) {
                    createLink(group, groupRefTenantId, LinkType.Ref, true).subscribe();
                }
                return Mono.just(group);
            });
    }
}
