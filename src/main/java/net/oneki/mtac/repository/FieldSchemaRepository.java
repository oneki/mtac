package net.oneki.mtac.repository;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.repository.framework.AbstractJoinRepository;
import net.oneki.mtac.model.entity.Ref;


@Repository
public class FieldSchemaRepository extends AbstractJoinRepository<Ref, Ref> {

	public FieldSchemaRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	protected String getLeftName() {
		return "field_id";
	}

	@Override
	protected String getRightName() {
		return "schema_id";
	}

	@Override
	protected String getTableName() {
		return "field_schema";
	}

}
