package net.oneki.mtac.framework.util.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class SqlReader {
	protected final static Pattern pattern = Pattern.compile("^(([^']+|'[^']*')*)--[^\r\n]*");

	public static String getSQL(String filepath) {
		try {
			Resource resource = new ClassPathResource("/sql/" + filepath, SqlReader.class);
			BufferedReader buffer = new BufferedReader(new InputStreamReader(resource.getInputStream()));
			return buffer.lines()
					.map(String::trim)
					.map(SqlReader::removeComment)
					.reduce("", (x, y) -> x.concat(' ' + y));

		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}

	}

	protected static String removeCommentRegexp(String line) {
		Matcher matcher = pattern.matcher(line);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, "$1");
		}
		matcher.appendTail(sb);
		var result = sb.toString();
		return result;
	}

	protected static String removeComment(String line) {
		var tokens = line.split("--");
		if (tokens.length == 0) {
			// the line is just "--"
			return "";
		}
		return tokens[0];
	}
}
