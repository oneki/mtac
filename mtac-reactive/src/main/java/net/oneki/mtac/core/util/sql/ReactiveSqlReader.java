package net.oneki.mtac.core.util.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import net.oneki.mtac.core.util.file.ReactiveFileUtils;
import net.oneki.mtac.framework.util.sql.SqlReader;
import reactor.core.publisher.Mono;

public class ReactiveSqlReader extends SqlReader {

	public static Mono<String> getReactiveSQL(String filepath) {
		try {
			Resource resource = new ClassPathResource("/sql/" + filepath, ReactiveSqlReader.class);
			BufferedReader buffer = new BufferedReader(new InputStreamReader(resource.getInputStream()));
			// Read the SQL file line by line, trim each line, remove comments, and concatenate
			return ReactiveFileUtils
					.readFileToFlux(buffer)
					.map(String::trim)
					.map(SqlReader::removeComment)
					.reduce("", (x, y) -> x.concat(' ' + y));
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}
}
