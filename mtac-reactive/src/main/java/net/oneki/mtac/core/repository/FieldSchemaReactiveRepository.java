package net.oneki.mtac.core.repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import net.oneki.mtac.core.repository.framework.AbstractJoinRepository;
import net.oneki.mtac.model.core.resource.Ref;


@Repository
public class FieldSchemaReactiveRepository extends AbstractJoinRepository<Ref, Ref> {

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
