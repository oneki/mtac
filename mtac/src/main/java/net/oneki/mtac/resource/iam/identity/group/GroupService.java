package net.oneki.mtac.resource.iam.identity.group;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.resource.LinkType;
import net.oneki.mtac.model.resource.iam.identity.Identity;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.model.resource.iam.identity.group.GroupUpsertRequest;
import net.oneki.mtac.resource.ResourceService;

public abstract class GroupService<U extends GroupUpsertRequest, E extends Group> extends ResourceService<U, E> {
    protected GroupMembershipRepository groupMembershipRepository;

    @Autowired
    public final void setGroupMembershipRepository(GroupMembershipRepository groupMembershipRepository) {
        this.groupMembershipRepository = groupMembershipRepository;
    }

    public E create(E resourceEntity, Integer groupRefTenantId) {
        return create(resourceEntity, groupRefTenantId, false);
    }

    public E create(E resourceEntity, Integer groupRefTenantId, boolean unsecure) {
        var group = unsecure ? super.createUnsecure(resourceEntity) : super.create(resourceEntity);
        if (groupRefTenantId != null) {
            createLink(group, groupRefTenantId, LinkType.Ref, true);
        }
        return group;
    }

    public void addMember(E group, Identity member) {
        addMember(group, member, false);
    }

    public void addMember(E group, Identity member, boolean unsecure) {
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        group.getMembers().add(member);
        if (unsecure) {
            updateUnsecure(group);
        } else {
            update(group);
        }

        // add the member to the group membership repository
        groupMembershipRepository.create(group.toRef(), member.toRef());
    }

    public void addMembers(E group, List<Identity> members) {
        addMembers(group, members, false);
    }

    public void addMembers(E group, List<Identity> members, boolean unsecure) {
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        group.getMembers().addAll(members);
        if (unsecure) {
            updateUnsecure(group);
        } else {
            update(group);
        }

        for (Identity member : members) {
            // add the member to the group membership repository
            groupMembershipRepository.create(group.toRef(), member.toRef());
        }
    }
}
