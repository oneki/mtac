package net.oneki.mtac.resource.iam.identity.group;

import java.util.ArrayList;
import java.util.List;

import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.iam.identity.Identity;
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

    public void addMember(E group, Identity member) {
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        group.getMembers().add(member);
        update(group);
    }

    public void addMembers(E group, List<Identity> members) {
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        group.getMembers().addAll(members);
        update(group);
    }

    public void addMembersUnsecure(E group, List<Identity> members) {
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        group.getMembers().addAll(members);
        updateUnsecure(group);
    }
}
