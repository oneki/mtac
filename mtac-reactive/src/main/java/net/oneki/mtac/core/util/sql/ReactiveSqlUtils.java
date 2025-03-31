package net.oneki.mtac.core.util.sql;

import net.oneki.mtac.framework.util.sql.SqlUtils;
import reactor.core.publisher.Mono;

public class ReactiveSqlUtils extends SqlUtils {

	public static Mono<String> getReactiveSQL(String path) {
		String result = sqlMap.get(path);
		if (result != null) {
			return Mono.just(result);
		}
		return ReactiveSqlReader.getReactiveSQL(path)
				.doOnNext(sql -> {
					sqlMap.put(path, sql);
				});
	}
}
