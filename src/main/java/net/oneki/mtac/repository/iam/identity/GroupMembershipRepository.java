package net.oneki.mtac.repository.iam.identity;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.repository.framework.AbstractJoinRepository;


@Repository
public class GroupMembershipRepository extends AbstractJoinRepository<Ref, Ref> {

	public GroupMembershipRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	protected String getLeftName() {
		return "parent_id";
	}

	@Override
	protected String getRightName() {
		return "child_id";
	}

	@Override
	protected String getTableName() {
		return "group_membership";
	}

}
