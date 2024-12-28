package net.oneki.mtac.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oneki.mtac.core.resource.HasId;
import net.oneki.mtac.core.resource.Ref;

public class EntityUtils {
	private final static Pattern validFieldPattern = Pattern.compile("([a-zA-Z$_][a-zA-Z0-9$_]*)(\\.[a-zA-Z$_][a-zA-Z0-9$_]*)*");
	public static Integer getId(Ref ref) {
		if (ref == null) return null;
		return ref.getId();
	}

	public static Integer getId(HasId entity) {
		if (entity == null) return null;
		return entity.getId();
	}

	public static boolean isValidField(String field) {
		Matcher matcher = validFieldPattern.matcher(field);
		return matcher.matches();
	}
		
}
