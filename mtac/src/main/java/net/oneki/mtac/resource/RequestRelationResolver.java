package net.oneki.mtac.resource;

import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;

/**
 * Resolves a relation of an upsert request in place of the default rule, which reads the related resource with the
 * rights of the user (a resource the user cannot read is refused with {@code RESOURCE_NOT_FOUND}).
 *
 * <p>For a relation that a user may keep without reading the related resource, e.g. the controller of a site, owned
 * by the company that installed it, when the site is saved by another company that manages it. An implementation
 * applies its own rule and returns the resource, or returns {@code null} to leave the relation to the default rule.
 *
 * <p>Every Spring bean implementing this interface is consulted, in their order, before the default rule.
 */
public interface RequestRelationResolver {

    /**
     * @param request      the upsert request being read
     * @param relationField the relation field of the request
     * @param relation     the reference sent (its id is set)
     * @return the related resource, or {@code null} to apply the default rule
     */
    Resource resolve(UpsertRequest request, ResourceField relationField, Resource relation);
}
