package net.oneki.mtac.framework.util.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.sqids.Sqids;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.introspect.ResourceDesc;
import net.oneki.mtac.model.core.framework.HasSchema;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.resource.Resource;

public class ResourceUtils {
	public static Sqids sqids;

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
			return get(resource, resourceDesc, tokenList);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new UnexpectedException("ILLEGAL_ACCESS",
					"Error while accessing field " + path + " in " + resourceClass.getSimpleName(), e);
		}
	}

	private static Object get(Object resource, ResourceDesc resourceDesc, List<String> pathTokens)
			throws IllegalArgumentException, IllegalAccessException {
		// pop first token
		var token = pathTokens.remove(0);
		// get the field
		var field = resourceDesc.getField(token);
		if (field == null) {
			return null;
		}
		// get the value
		var value = field.getValue(resource);
		if (value == null)
			return null;
		if (pathTokens.isEmpty())
			return value;

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
			} catch (NumberFormatException e) {
			}
		}

		return get(value, nextResourceDesc, pathTokens);

	}

	private static Set<String> refFields = Set.of("id", "uid", "label", "schema", "pub", "link", "tenant");

	public static boolean isRef(Object resource) {
		if (resource instanceof HasSchema) {
			var resourceDesc = ResourceRegistry.getResourceDesc(resource.getClass());
			return !resourceDesc.getFields().stream().anyMatch(f -> f.getValue(resource) != null &&
					!refFields.contains(f.getLabel()));
		}
		return false;
	}

	private static Set<String> softRelationFields = Set.of("label", "schema");

	public static boolean isSoftRelation(Object resource) {
		if (resource instanceof HasSchema) {
			var resourceDesc = ResourceRegistry.getResourceDesc(resource.getClass());
			return !resourceDesc.getFields().stream().anyMatch(f -> f.getValue(resource) != null &&
					!softRelationFields.contains(f.getLabel()));
		}
		return false;
	}

	public static void initSqids(String alphabet) {
		if (sqids == null) {
			sqids = Sqids.builder()
					.minLength(7)
					.alphabet(alphabet)
					.build();
		}
	}

	private static Set<String> toRefFields = Set.of("id", "uid", "label", "schemaId",  "tenantId", "schemaLabel", "tenantLabel");
	public static <T extends Resource> T toRef(T resource) {
		if (resource == null) {
			return resource;
		}

		var resourceDesc = ResourceRegistry.getResourceDesc(resource.getClass());
		// loop over all fields and set the value to null if not id, uid, label, schema or tenant
		for (var field : resourceDesc.getFields()) {
			if (field.getValue(resource) == null || toRefFields.contains(field.getLabel())) {
				continue;
			}
			if (field.isMultiple()) {
				field.setValue(resource, new ArrayList<>());
			} else {
				try {
					field.getField().setAccessible(true);
					field.setValue(resource, null);
				} catch (Exception e) {
					if (field.getValue(resource) instanceof Number) {
						field.setValue(resource, 0);
					}
				}
			}
		}

		return resource;
	}

	// public static void main(String[] args) {
	// Sqids sqids = Sqids.builder()
	// .minLength(7)
	// .alphabet("FxnXM1kBN6cuhsAvjW3Co7l2RePyY8DwaU04Tzt9fHQrqSVKdpimLGIJOgb5ZE")
	// .build();
	// for (int i=0; i<100; i++) {
	// String id = sqids.encode(Arrays.asList(Long.valueOf(Integer.MAX_VALUE-1)));
	// System.out.println(id);
	// System.out.println(sqids.decode(id));
	// }
	// }
}
