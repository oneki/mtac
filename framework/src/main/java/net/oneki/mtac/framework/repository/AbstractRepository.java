package net.oneki.mtac.framework.repository;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;


public abstract class AbstractRepository {
	protected NamedParameterJdbcTemplate jdbcTemplate;
	protected DataSource dataSource;
	protected Map<String, String> sqlMap = new HashMap<>();

	@Autowired
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
		this.jdbcTemplate.getJdbcTemplate().setQueryTimeout(30);
		this.dataSource = dataSource;
	}

	

}
