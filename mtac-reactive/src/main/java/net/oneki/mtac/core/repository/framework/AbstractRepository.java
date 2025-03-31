package net.oneki.mtac.core.repository.framework;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;


public abstract class AbstractRepository {
	protected DatabaseClient db;
	protected Map<String, String> sqlMap = new HashMap<>();

	@Autowired
	public void setDatabaseClient(DatabaseClient db) {
		this.db = db;
	}

	

}
