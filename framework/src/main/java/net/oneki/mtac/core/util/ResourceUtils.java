package net.oneki.mtac.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.oneki.mtac.core.framework.HasSchema;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.core.util.exception.UnexpectedException;
import net.oneki.mtac.core.util.introspect.ResourceDesc;

public class ResourceUtils {
	public static <T extends HasSchema> List<Object> get(Collection<T> resource, String path) {
		var result = new ArrayList<>();
		for (var r : resource) {
			result.add(get(r, path));
		}
		return result;
	}

	public static <T extends HasSchema> Object get(T resource, String path) {
		var resourceClass = resource.getClass();
		var resourceDesc = ResourceRegistry.getResourceDesc(resourceClass);
		try {
			var tokens = path.split("\\.");
			var tokenList = new ArrayList<String>(tokens.length);
			for (var token : tokens) {
				tokenList.add(token);
			}
			return get(resource, resourceDesc,  tokenList);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new UnexpectedException("ILLEGAL_ACCESS", "Error while accessing field " + path + " in " + resourceClass.getSimpleName(), e);
		}
	}

	private static Object  get(Object resource, ResourceDesc resourceDesc,  List<String> pathTokens) throws IllegalArgumentException, IllegalAccessException {
		// pop first token
		var token = pathTokens.remove(0);
		// get the field
		var field = resourceDesc.getField(token);
		// get the value
		var value = field.getValue(resource);
		if (value == null) return null;
		if (pathTokens.isEmpty()) return value;

		var nextResourceDesc = ResourceRegistry.getResourceDesc(field.getType());
		if (field.isMultiple()) {
			var nextPath = pathTokens.get(0);
			try {
				var index = Integer.parseInt(nextPath);
				if (value instanceof List) {
					var list = (List<?>) value;
					if (index < list.size()) {
						pathTokens.remove(0);
						return get(list.get(index), nextResourceDesc, pathTokens);
					} else {
						return null;
					}
				}
			} catch (NumberFormatException e) {}
		}

		return get(value, nextResourceDesc, pathTokens);
		
	}

	private static Set<String> refFields =  Set.of("id", "label", "schema", "pub", "link", "urn", "tenant");
    public static boolean isRef(Object resource) {
		if (resource instanceof HasSchema) {
			var resourceDesc = ResourceRegistry.getResourceDesc(resource.getClass());
			return !resourceDesc.getFields().stream().anyMatch(f -> 
				f.getValue(resource) != null &&
				!refFields.contains(f.getLabel())
			);
		}
		return false;
    }

	private static Set<String> softRelationFields =  Set.of("label", "schema");
	public static boolean isSoftRelation(Object resource) {
		if (resource instanceof HasSchema) {
			var resourceDesc = ResourceRegistry.getResourceDesc(resource.getClass());
			return !resourceDesc.getFields().stream().anyMatch(f -> 
				f.getValue(resource) != null &&
				!softRelationFields.contains(f.getLabel())
			);
		}
		return false;
    }
}
