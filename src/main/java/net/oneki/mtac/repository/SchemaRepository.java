package net.oneki.mtac.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.config.Constants;
import net.oneki.mtac.model.entity.SchemaEntity;
import net.oneki.mtac.util.sql.SqlUtils;

@Repository
@RequiredArgsConstructor
public class SchemaRepository {
    private final ResourceRepository resourceRepository;

    public List<SchemaEntity> listAllSchemasUnsecure() {
        var sql = SqlUtils.getSQL("resource/resource_list_all_schema_unsecure.sql");
        return resourceRepository.list(Constants.TENANT_ROOT_ID, SchemaEntity.class, sql);
    }

    public void delete(Integer schemaId) {
        resourceRepository.delete(schemaId);
    }

}
