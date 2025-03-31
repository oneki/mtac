package net.oneki.mtac.framework.repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import net.oneki.mtac.model.core.resource.Ref;


public abstract class AbstractJoinRepository<L extends Ref, R extends Ref> {
	protected NamedParameterJdbcTemplate jdbcTemplate;
	protected DataSource dataSource;
	protected String insertSQL;
	protected String deleteSQL;
	protected String deleteSQLByLeft;
	protected String deleteSQLByRight;
	protected String ListByRightSQL;
	protected String ListByLeftSQL;

	protected abstract String getLeftName();
	protected abstract String getRightName();
	protected abstract String getTableName();

	public AbstractJoinRepository(DataSource dataSource) {
		this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
		this.dataSource = dataSource;
	}

	public void create(L leftEntity, R rightEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("leftId", leftEntity.getId());
		args.put("rightId", rightEntity.getId());
		jdbcTemplate.update(getInsertSql(), args);
	}

	public void delete(L leftEntity, R rightEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("leftId", leftEntity.getId());
		args.put("rightId", rightEntity.getId());
		jdbcTemplate.update(getDeleteSQL(), args);
	}

	public void delete(L leftEntity, Integer rightEntityId) {
		Map<String, Object> args = new HashMap<>();
		args.put("leftId", leftEntity.getId());
		args.put("rightId", rightEntityId);
		jdbcTemplate.update(getDeleteSQL(), args);
	}

	public void delete(Integer leftEntityId, R rightEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("leftId", leftEntityId);
		args.put("rightId", rightEntity.getId());
		jdbcTemplate.update(getDeleteSQL(), args);
	}

	@Transactional
	public void createByLeft(L leftEntity, Set<R> rightEntities) {
		if (rightEntities != null) {
			for (R rightEntity : rightEntities) {
				create(leftEntity, rightEntity);
			}
		}
	}

	@Transactional
	public void createByRight(R rightEntity, Set<L> leftEntities) {
		if (leftEntities != null) {
			for (L leftEntity : leftEntities) {
				create(leftEntity, rightEntity);
			}
		}
	}

	@Transactional
	public void updateByLeft(L leftEntity, Set<R> rightEntities) {
		// Set all current right Entities Id related to leftEntity
		Set<Integer> currentRightEntitiesId = listByLeft(leftEntity);
		Set<R> toAdd = new HashSet<>();

		// add missing rightEntities
		for (R rightEntity : rightEntities) {
			if (currentRightEntitiesId.contains(rightEntity.getId())) {
				// nothing to do, we just remove the Id from the Set
				currentRightEntitiesId.remove(rightEntity.getId());
			} else {
				// we need to add this new entry
				toAdd.add(rightEntity);
			}
		}

		// remove deprecated rightEntities
		for (Integer id : currentRightEntitiesId) {
			delete(leftEntity, id);
		}

		for (R rightEntity : toAdd) {
			create(leftEntity, rightEntity);
		}
	}

	@Transactional
	public void updateByLeft(L leftEntity, R rightEntity) {
		// Set all current right Entities Id related to leftEntity
		Set<Integer> currentRightEntitiesId = listByLeft(leftEntity);
		boolean toAdd = false;

		// add missing rightEntities

		if (currentRightEntitiesId.contains(rightEntity.getId())) {
			// nothing to do, we just remove the Id from the Set
			currentRightEntitiesId.remove(rightEntity.getId());
		} else {
			// we need to add this new entry
			toAdd = true;
		}

		// remove deprecated rightEntities
		for (Integer id : currentRightEntitiesId) {
			delete(leftEntity, id);
		}

		if (toAdd) {
			create(leftEntity, rightEntity);
		}
	}

	@Transactional
	public void updateByRight(R rightEntity, Set<L> leftEntities) {
		// Set all current left Entities Id related to rightEntity
		Set<Integer> currentLeftEntitiesId = listByRight(rightEntity);
		Set<L> toAdd = new HashSet<>();

		// add missing leftEntities
		for (L leftEntity : leftEntities) {
			if (currentLeftEntitiesId.contains(leftEntity.getId())) {
				// nothing to do, we just remove the Id from the Set
				currentLeftEntitiesId.remove(leftEntity.getId());
			} else {
				// we need to add this new entry
				toAdd.add(leftEntity);
			}
		}

		// remove deprecated leftEntities
		for (Integer id : currentLeftEntitiesId) {
			delete(id, rightEntity);
		}

		for (L leftEntity : toAdd) {
			create(leftEntity, rightEntity);
		}
	}

	@Transactional
	public void updateByRight(R rightEntity, L leftEntity) {
		// Set all current left Entities Id related to rightEntity
		Set<Integer> currentLeftEntitiesId = listByRight(rightEntity);
		boolean toAdd = false;

		// add missing leftEntities
		if (currentLeftEntitiesId.contains(leftEntity.getId())) {
			// nothing to do, we just remove the Id from the Set
			currentLeftEntitiesId.remove(leftEntity.getId());
		} else {
			// we need to add this new entry
			toAdd = true;
		}

		// remove deprecated leftEntities
		for (Integer id : currentLeftEntitiesId) {
			delete(id, rightEntity);
		}

		if (toAdd) {
			create(leftEntity, rightEntity);
		}
	}

	public void deleteByLeft(L leftEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("id", leftEntity.getId());
		jdbcTemplate.update(getDeleteByLeftSQL(), args);
	}

	public void deleteByRight(R rightEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("id", rightEntity.getId());
		jdbcTemplate.update(getDeleteByRightSQL(), args);
	}

	public Set<Integer> listByRight(R rightEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("id", rightEntity.getId());
		return new HashSet<>(jdbcTemplate.query(getListByRightSQL(), args, new SingleColumnRowMapper<Integer>()));
	}

	public Set<Integer> listByLeft(L leftEntity) {
		Map<String, Object> args = new HashMap<>();
		args.put("id", leftEntity.getId());
		return new HashSet<>(jdbcTemplate.query(getListByLeftSQL(), args, new SingleColumnRowMapper<Integer>()));
	}

	protected String getInsertSql() {
		if (insertSQL == null) {
			insertSQL = "INSERT INTO " + getTableName() + "(" + getLeftName() + ", " + getRightName() + ") VALUES(:leftId, :rightId)";
		}
		return insertSQL;
	}

	protected String getDeleteSQL() {
		if (deleteSQL == null) {
			deleteSQL = "DELETE FROM " + getTableName() + " WHERE " + getLeftName() + " = :leftId AND " + getRightName() + " = :rightId";
		}
		return deleteSQL;
	}

	protected String getDeleteByLeftSQL() {
		if (deleteSQLByLeft == null) {
			deleteSQLByLeft = "DELETE FROM " + getTableName() + " WHERE " + getLeftName() + " = :id";
		}

		return deleteSQLByLeft;
	}

	protected String getDeleteByRightSQL() {
		if (deleteSQLByRight == null) {
			deleteSQLByRight = "DELETE FROM " + getTableName() + " WHERE " + getRightName() + " = :id";
		}

		return deleteSQLByRight;
	}

	protected String getListByRightSQL() {
		if (ListByRightSQL == null) {
			ListByRightSQL = "SELECT " + getLeftName() + " FROM " + getTableName() + " WHERE " + getRightName() + " = :id";
		}
		return ListByRightSQL;
	}

	protected String getListByLeftSQL() {
		if (ListByLeftSQL == null) {
			ListByLeftSQL = "SELECT " + getRightName() + " FROM " + getTableName() + " WHERE " + getLeftName() + " = :id";
		}
		return ListByLeftSQL;
	}

}
