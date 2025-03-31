package net.oneki.mtac.core.repository.framework;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.Transactional;

import net.oneki.mtac.model.core.resource.Ref;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractJoinRepository<L extends Ref, R extends Ref> {
	protected DatabaseClient db;
	protected String insertSQL;
	protected String deleteSQL;
	protected String deleteSQLByLeft;
	protected String deleteSQLByRight;
	protected String ListByRightSQL;
	protected String ListByLeftSQL;

	protected abstract String getLeftName();

	protected abstract String getRightName();

	protected abstract String getTableName();

	public AbstractJoinRepository(DatabaseClient db) {
		this.db = db;
	}

	public Mono<Long> create(L leftEntity, R rightEntity) {
		return db.sql(getInsertSql())
				.bind("leftId", leftEntity.getId())
				.bind("rightId", rightEntity.getId())
				.fetch()
				.rowsUpdated();
	}

	public Mono<Long> delete(L leftEntity, R rightEntity) {
		return db.sql(getDeleteSQL())
				.bind("leftId", leftEntity.getId())
				.bind("rightId", rightEntity.getId())
				.fetch()
				.rowsUpdated();
	}

	public Mono<Long> delete(L leftEntity, Integer rightEntityId) {
		return db.sql(getDeleteSQL())
				.bind("leftId", leftEntity.getId())
				.bind("rightId", rightEntityId)
				.fetch()
				.rowsUpdated();
	}

	public Mono<Long> delete(Integer leftEntityId, R rightEntity) {
		return db.sql(getDeleteSQL())
				.bind("leftId", leftEntityId)
				.bind("rightId", rightEntity.getId())
				.fetch()
				.rowsUpdated();
	}

	@Transactional
	public Mono<Long> createByLeft(L leftEntity, Set<R> rightEntities) {
		if (rightEntities != null) {
			return Flux.fromIterable(rightEntities)
					.flatMap(rightEntity -> create(leftEntity, rightEntity))
					// sum the results
					.reduce(0L, (a, b) -> a + b);
		}
		return Mono.just(0L);
	}

	@Transactional
	public Mono<Long> createByRight(R rightEntity, Set<L> leftEntities) {
		if (leftEntities != null) {
			return Flux.fromIterable(leftEntities)
					.flatMap(leftEntity -> create(leftEntity, rightEntity))
					// sum the results
					.reduce(0L, (a, b) -> a + b);
		}
		return Mono.just(0L);
	}

	public Mono<Long> deleteByLeft(L leftEntity) {
		return db.sql(getDeleteByLeftSQL())
				.bind("id", leftEntity.getId())
				.fetch()
				.rowsUpdated();
	}

	public Mono<Long> deleteByRight(R rightEntity) {
		return db.sql(getDeleteByRightSQL())
				.bind("id", rightEntity.getId())
				.fetch()
				.rowsUpdated();
	}

	public Mono<Set<Integer>> listByRight(R rightEntity) {
		return db.sql(getListByRightSQL())
				.bind("id", rightEntity.getId())
				.map(row -> row.get(getLeftName(), Integer.class))
				.all()
				.collectList()
				.map(list -> new HashSet<>(list));
	}

	public Mono<Set<Integer>> listByLeft(L leftEntity) {
		return db.sql(getListByLeftSQL())
				.bind("id", leftEntity.getId())
				.map(row -> row.get(getRightName(), Integer.class))
				.all()
				.collectList()
				.map(list -> new HashSet<>(list));
	}

	@Transactional
	public Mono<Void> updateByLeft(L leftEntity, Set<R> rightEntities) {
		var currentRightEntitiesId = listByLeft(leftEntity).block();
		Flux.fromIterable(rightEntities)
				.filter(rightEntity -> {
					if (currentRightEntitiesId.contains(rightEntity.getId())) {
						// nothing to do, we just remove the Id from the Set
						currentRightEntitiesId.remove(rightEntity.getId());
						return false;
					} else {
						// we need to add this new entry
						return true;
				 	}
				})
				.flatMap(rightEntity -> create(leftEntity, rightEntity))
				.thenMany(
						// remove deprecated rightEntities
						Flux.fromIterable(currentRightEntitiesId)
								.flatMap(id -> delete(leftEntity, id)))
				.then();

		return Flux.fromIterable(currentRightEntitiesId)
				.flatMap(id -> delete(leftEntity, id))
				.then();
	}

	@Transactional
	public Mono<Void> updateByLeft(L leftEntity, R rightEntity) {
		var currentRightEntitiesId = listByLeft(leftEntity).block();
		boolean toAdd = false;

		// add missing rightEntities

		if (currentRightEntitiesId.contains(rightEntity.getId())) {
			// nothing to do, we just remove the Id from the Set
			currentRightEntitiesId.remove(rightEntity.getId());
		} else {
			// we need to add this new entry
			toAdd = true;
		}

		Mono<?> create = Mono.empty();
		if (toAdd) {
			create = create(leftEntity, rightEntity);
		}
		return create
			.thenMany(
					Flux.fromIterable(currentRightEntitiesId)
							.flatMap(id -> delete(leftEntity, id)))
			.then();
	}

	@Transactional
	public void updateByRight(R rightEntity, Set<L> leftEntities) {
		// same as updateByLeft but for rightEntity
		// Set all current right Entities Id related to leftEntity
		var currentLeftEntitiesId = listByRight(rightEntity).block();
		Flux.fromIterable(leftEntities)
				.filter(leftEntity -> {
					if (currentLeftEntitiesId.contains(leftEntity.getId())) {
						// nothing to do, we just remove the Id from the Set
						currentLeftEntitiesId.remove(leftEntity.getId());
						return false;
					} else {
						// we need to add this new entry
						return true;
					}
				})
				.flatMap(leftEntity -> create(leftEntity, rightEntity))
				.thenMany(
						// remove deprecated leftEntities
						Flux.fromIterable(currentLeftEntitiesId)
								.flatMap(id -> delete(id, rightEntity)))
				.then();
	}

	@Transactional
	public Mono<Void> updateByRight(R rightEntity, L leftEntity) {
		// same as updateByLeft but for rightEntity
		var currentLeftEntitiesId = listByRight(rightEntity).block();
		boolean toAdd = false;
		// add missing leftEntities
		if (currentLeftEntitiesId.contains(leftEntity.getId())) {
			// nothing to do, we just remove the Id from the Set
			currentLeftEntitiesId.remove(leftEntity.getId());
		} else {
			// we need to add this new entry
			toAdd = true;
		}


		Mono<?> create = Mono.empty();
		if (toAdd) {
			create = create(leftEntity, rightEntity);
		}
		return create
			.thenMany(
					// remove deprecated leftEntities
					Flux.fromIterable(currentLeftEntitiesId)
							.flatMap(id -> delete(id, rightEntity)))
			.then();
	}

	protected String getInsertSql() {
		if (insertSQL == null) {
			insertSQL = "INSERT INTO " + getTableName() + "(" + getLeftName() + ", " + getRightName()
					+ ") VALUES(:leftId, :rightId)";
		}
		return insertSQL;
	}

	protected String getDeleteSQL() {
		if (deleteSQL == null) {
			deleteSQL = "DELETE FROM " + getTableName() + " WHERE " + getLeftName() + " = :leftId AND " + getRightName()
					+ " = :rightId";
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
			ListByRightSQL = "SELECT " + getLeftName() + " FROM " + getTableName() + " WHERE " + getRightName()
					+ " = :id";
		}
		return ListByRightSQL;
	}

	protected String getListByLeftSQL() {
		if (ListByLeftSQL == null) {
			ListByLeftSQL = "SELECT " + getRightName() + " FROM " + getTableName() + " WHERE " + getLeftName()
					+ " = :id";
		}
		return ListByLeftSQL;
	}

}
