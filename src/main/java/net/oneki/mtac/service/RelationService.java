package net.oneki.mtac.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.model.framework.HasSchema;
import net.oneki.mtac.model.framework.RelationLabel;
import net.oneki.mtac.model.framework.RelationRefs;
import net.oneki.mtac.model.framework.Relations;
import net.oneki.mtac.model.framework.ResourceEmbedded;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.util.ResourceUtils;
import net.oneki.mtac.util.SetUtils;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.introspect.ResourceField;

@Service
@RequiredArgsConstructor
public class RelationService {
	private final ResourceRepository resourceRepository;

	public <T extends HasSchema> List<T> populateRelations(List<T> resources, Set<String> relationNames) {
		if (resources == null || resources.size() == 0) {
			return resources;
		}
		var relations = loadRelations(resources, relationNames);
		for (var relationName : relationNames) {
			populateRelations(resources, relationName, relations);
		}
		return resources;
	}

	private <T extends HasSchema> List<T> populateRelations(List<T> resources, String relationName, Relations relations) {
		var tokens = relationName.split("\\.");
		final String subRelationName = (tokens.length > 1)  ? String.join(".", Arrays.copyOfRange(tokens, 1, tokens.length)) : null;
		for (var resource : resources) {
			var resourceDesc = ResourceRegistry.getResourceDesc(resource.getClass());
			var field = resourceDesc.getField(tokens[0]);
			if (field.isMultiple()) {
				var value = field.getValue(resource);
				if (value instanceof List) {
					field.setValue(resource, ((List<?>) value).stream()
						.map(item -> {
							var relation = populateRelation(item, field, relations, subRelationName, true);
							if (relation == null) return item;
							return relation;
						})
						.collect(Collectors.toList())
					);
				}
			} else {
				populateRelation(resource, field, relations, subRelationName, false);
			}
		}

		return resources;
	}

	private HasSchema populateRelation(Object resource, ResourceField field, Relations relations, String subRelationName, boolean inList) {
		if (ResourceUtils.isRef(resource)) {
			switch(resource) {
				case ResourceEntity ref -> {
					ResourceEntity relation = null;
					if (ref.getId() != null) {
						relation = relations.getResourceEntityById(ref.getId());
					} else {
						relation = relations.getResourceEntityByLabel(RelationLabel.builder()
							.label(ref.getLabel())
							.schema(ref.getClass())
							.tenantLabel(ref.getTenant())
							.build()
						);
					}
					if (relation != null) {
						if(subRelationName != null) {
							populateRelations(List.of(relation), subRelationName, relations);
						}
						if (!inList) {
							field.setValue(resource, relation);
						}
						return relation;
					}
				}
				case ResourceEmbedded embedded -> {
					if (subRelationName != null) {
						populateRelations(List.of(embedded), subRelationName, relations);
						
					}
					return embedded;
				}
				default -> {}
			}
		}
		return null;
	}

	public <T extends HasSchema> T populateRelations(T resource, Set<String> relationNames) {
		return populateRelations(List.of(resource), relationNames).get(0);
	}

    private Relations loadRelations(List<? extends HasSchema> resources, Set<String> relationNames) {
		if (relationNames == null) {
			return null;
		}
		Map<String, RelationRefs> subRelations = new HashMap<>();
		RelationRefs relationsRefs = new RelationRefs();
		Relations result = new Relations();
		Map<String, List<ResourceEntity>> subRelationsToLoad = new HashMap<>();
		if (resources != null && resources.size() > 0) {
			for (HasSchema resource : resources) {
				for (String relationName : relationNames) {
					String[] tokens = relationName.split("\\.");
					// we can only load first level relation
					var owner = resource;
					if (isResourceEmbedded(owner, tokens[0])) {
						addRelationsToLoadEmbedded(subRelationsToLoad, owner, relationName);
					} else {
						RelationRefs relationRefs = getRelationRefs(owner, tokens[0]);

						if (tokens.length > 1) {
							String subRelationName = String.join(".", Arrays.copyOfRange(tokens, 1, tokens.length));
							RelationRefs currentSubRelationRefs = subRelations.getOrDefault(subRelationName, new RelationRefs());
							currentSubRelationRefs.add(relationRefs);
							subRelations.put(subRelationName, currentSubRelationRefs);
						}
						relationsRefs.add(relationRefs);
					}
				}
			}
			var relations = new ArrayList<ResourceEntity>();

			if (relationsRefs.getIds().size() > 0) {
				var relationsById = resourceRepository.listbyIds(new HashSet<>(relationsRefs.getIds()));
				for (ResourceEntity relation : relationsById) {
					result.putId(relation.getId(), relation);
					// if (relation.getLinkId() != null && result.getResourceEntityById(relation.getLinkId()) == null) {
					// 	result.putId(relation.getLinkId(), relation);
					// }
				}
				relations.addAll(relationsById);
			}

			for (var relationRef : relationsRefs.getLabels()) {
				
				var relation = resourceRepository.getByLabel(relationRef.getLabel(), relationRef.getTenantLabel(), relationRef.getSchema());
				if (relation != null) {
					result.putLabel(relationRef, relation);
					relations.add(relation);
				}
			}

			for (var entry : subRelations.entrySet()) {
				// relationName represent the subrelation key
				// if we look for nics.subnet, it will be subnet
				var relationName = entry.getKey();

				// subRelationRefs represent all the ID or triplet label/schema/tenant of the
				// nics
				var subRelationRefs = entry.getValue();

				// we try to convert these NICs ref to actual NICs object (since we have loaded
				// them just above)
				List<ResourceEntity> subResourceEntitys = new ArrayList<>();
				for (ResourceEntity relation : relations) {
					var relationClass = relation.getClass();

					if (subRelationRefs.containsId(relation.getId())
							|| subRelationRefs.containsLabel(relation.getLabel(), relationClass)) {
						subResourceEntitys.add(relation);
					}
				}

				result.add(loadRelations(subResourceEntitys, SetUtils.of(relationName)));
			}

		}

		// managed embedded resources
		for (Map.Entry<String, List<ResourceEntity>> entry : subRelationsToLoad.entrySet()) {
			result.add(loadRelations(entry.getValue(), SetUtils.of(entry.getKey())));
		}

		return result;
	}

    @SuppressWarnings({ "unchecked", "rawtypes" })
	private boolean isResourceEmbedded(Object owner, String path) {
		if (!(owner instanceof HasSchema)) {
			return false;
		}
		Object embedded = ResourceUtils.get((HasSchema) owner, path);
		return switch(embedded) {
			case null -> false;
			case List embeddedList -> {
				if (embeddedList.size() == 0) {
					yield false;
				}
				embedded = ((List<Object>) embeddedList).get(0);
				yield embedded instanceof ResourceEmbedded;
			}
			case ResourceEmbedded embeddedResource -> true;
			default -> false;
		};
	}

    @SuppressWarnings("unchecked")
	/**
	 * Check which resource should really be loaded from the DB
	 * If the relation is a embedded object, it means that this object is already loaded
	 * This embedded object can contains other embedded object or relations
	 * We need to do a recursion until we find a relation -> this relation is saved in the embedded parameter
	 */
	private void addRelationsToLoadEmbedded(Map<String, List<ResourceEntity>> relationsToLoad, Object resource, String relationName) {
		if (!(resource instanceof HasSchema)) {
			return;
		}
		String[] tokens = relationName.split("\\.");
		String path = tokens[0];
		Object sub = ResourceUtils.get((HasSchema) resource, path);
		if (tokens.length > 1)  {
			String subRelationName = String.join(".", Arrays.copyOfRange(tokens, 1, tokens.length));
			if (sub instanceof List) {
				for (int i = 0; i < ((List<Object>) sub).size(); i++) {
					var owner = ((List<Object>) sub).get(i);
					if (isResourceEmbedded(owner, tokens[1])) {
						addRelationsToLoadEmbedded(relationsToLoad, owner, subRelationName);
					} else if (owner instanceof ResourceEntity) {
						List<ResourceEntity> embeddedRelationsToLoad = relationsToLoad.getOrDefault(subRelationName, new ArrayList<>());
						embeddedRelationsToLoad.add((ResourceEntity) owner);
						relationsToLoad.put(subRelationName, embeddedRelationsToLoad);
					}
				}
			} else {
				if (isResourceEmbedded(sub, tokens[1])) {
					addRelationsToLoadEmbedded(relationsToLoad, sub, subRelationName);
				} else if (sub instanceof ResourceEntity) {
					List<ResourceEntity> embeddedRelationsToLoad = relationsToLoad.getOrDefault(subRelationName, new ArrayList<>());
					embeddedRelationsToLoad.add((ResourceEntity) sub);
					relationsToLoad.put(subRelationName, embeddedRelationsToLoad);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private RelationRefs getRelationRefs(Object resource, String path) {
		RelationRefs result = new RelationRefs();
		if (!(resource instanceof HasSchema)) {
			return result;
		}
		
		Object ref =  ResourceUtils.get((HasSchema) resource, path);
		if (ref instanceof List) {
			var refList = (List<Object>) ref;
			for (int i = 0; i < refList.size(); i++) {
				var sub = refList.get(i);
				addRelationRef(result, sub);
			}
		} else {
			addRelationRef(result, ref);
		}
		return result;
	}

	private void addRelationRef(RelationRefs relationRefs, Object sub) {
		switch (sub) {
			case null -> {}
			case ResourceEntity resource -> {
				var id = resource.getId();
				if (id != null) {
					relationRefs.addId(id);
				} else {
					relationRefs.addLabel(RelationLabel.builder()
						.label(resource.getLabel())
						.schema(resource.getClass())
						.tenantLabel(resource.getTenant())
						.build()
					);
				}
			}
			case HasSchema embedded -> relationRefs.addEmbedded(embedded);
			default -> {}
		}
	}
}
