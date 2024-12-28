package net.oneki.mtac.resource.iam.identity;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import net.oneki.mtac.core.repository.framework.AbstractJoinRepository;
import net.oneki.mtac.core.resource.Ref;


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
