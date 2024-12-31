package net.oneki.mtac.resource.iam.identity.group;

import org.springframework.stereotype.Service;

import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.UpsertRequest;

@Service
public class GroupRefService<E extends GroupRef> extends ResourceService<UpsertRequest, E>{
}
