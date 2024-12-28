package net.oneki.mtac.core.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.core.Constants;
import net.oneki.mtac.core.util.sql.SqlUtils;
import net.oneki.mtac.resource.schema.Schema;

@Repository
@RequiredArgsConstructor
public class SchemaRepository {
    private final ResourceRepository resourceRepository;

    public List<Schema> listAllSchemasUnsecure() {
        var sql = SqlUtils.getSQL("resource/resource_list_all_schema_unsecure.sql");
        return resourceRepository.list(Constants.TENANT_ROOT_ID, Schema.class, sql);
    }

    public void delete(Integer schemaId) {
        resourceRepository.delete(schemaId);
    }

}
