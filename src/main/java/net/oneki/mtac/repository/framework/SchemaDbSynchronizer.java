package net.oneki.mtac.repository.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.config.Constants;
import net.oneki.mtac.model.entity.Field;
import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.model.entity.Resource;
import net.oneki.mtac.model.entity.Schema;
import net.oneki.mtac.model.framework.SyncDbContext;
import net.oneki.mtac.repository.FieldRepository;
import net.oneki.mtac.repository.FieldSchemaRepository;
import net.oneki.mtac.repository.ResourceRepository;
import net.oneki.mtac.repository.SchemaRepository;
import net.oneki.mtac.util.cache.Cache;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.exception.UnexpectedException;

@Component
@RequiredArgsConstructor
public class SchemaDbSynchronizer {
    private final ResourceRepository resourceRepository;
    private final FieldRepository fieldRepository;
    private final FieldSchemaRepository fieldSchemaRepository;
    private final SchemaRepository schemaRepository;
    protected final Cache cache;

    public void syncSchemaToDb() {

        var classIndex = ResourceRegistry.getClassindex();
        var schemaIndex = ResourceRegistry.getSchemaIndex();
        var scannedSchemaLabels = classIndex.keySet();
        var dbSchemaLabels = cache.getSchemas().values().stream().map(Schema::getLabel)
                .collect(Collectors.toSet());

        for (var scannedSchemaLabel : scannedSchemaLabels) {
            if (scannedSchemaLabel.startsWith("req."))
                continue;

            var resourceClass = classIndex.get(scannedSchemaLabel);
            var parentResourceClass = resourceClass.getSuperclass();
            var parentSchemaLabel = schemaIndex.get(parentResourceClass);
            var parents = new ArrayList<Ref>();
            if (parentSchemaLabel != null) {
                var parentSchemaEntityId = cache.getSchemaId(parentSchemaLabel);
                parents.add(Ref.builder()
                        .id(parentSchemaEntityId)
                        .label(parentSchemaLabel)
                        .schema(Constants.SCHEMA_SCHEMA_ID)
                        .tenant(Constants.TENANT_ROOT_ID)
                        .build());
            }

            if (!dbSchemaLabels.contains(scannedSchemaLabel)) {
                var schemaEntity = resourceRepository.create(Schema.builder()
                        .pub(true)
                        .urn("urn:root:schema:" + scannedSchemaLabel)
                        .name(scannedSchemaLabel)
                        .parents(parents)
                        .build());
                cache.addSchema(schemaEntity);
            } else {
                // compare parents
                var schemaEntity = cache.getSchemaByLabel(scannedSchemaLabel);
                var parentsInDb = schemaEntity.getParents();
                if (parentsInDb.containsAll(parents) && parents.containsAll(parentsInDb)) {
                    continue;
                }
                schemaEntity.setParents(parents);
                resourceRepository.update(schemaEntity);
            }
        }

        for (var dbSchemaLabel : dbSchemaLabels) {
            if (scannedSchemaLabels.contains(dbSchemaLabel)) {
                continue;
            }
            schemaRepository.delete(cache.getSchemaId(dbSchemaLabel));
        }
        fieldRepository.deleteOrphans();
    }

    public void syncFieldsToDb() {
        var context = new SyncDbContext();
        var scannedSchemaLabels = ResourceRegistry.getClassindex().keySet();
        context.setFieldEntities(fieldRepository.listAllFieldsUnsecure());

        for (var fieldEntity : context.getFieldEntities()) {
            for (var schema : fieldEntity.getSchemas()) {
                context.getFieldSchemaIndex().put(schema + ":" + fieldEntity.getLabel(), fieldEntity);
            }
            context.getFieldIndex().put(fieldEntity.getId(), fieldEntity);
        }

        for (var scannedSchemaLabel : scannedSchemaLabels) {
            syncFieldsToDb(context, scannedSchemaLabel);
        }
        removeFieldsFromDb(context);
        linkFieldsToSchemas(context);
        syncFieldsPeer(context);
    }

    private List<Field> syncFieldsToDb(SyncDbContext context, String scannedSchemaLabel) {
        var resourceDescs = ResourceRegistry.getResourceDescs();
        var result = new ArrayList<Field>();
        if (scannedSchemaLabel.startsWith("req.")) {
            return result;
        }
        var resourceDesc = resourceDescs.get(scannedSchemaLabel);
        if (context.getNextFieldEntitiesByResourceDesc().containsKey(resourceDesc)) {
            return context.getNextFieldEntitiesByResourceDesc().get(resourceDesc);
        }

        for (var scannedField : resourceDesc.getFields()) {
            var nextFieldEntity = syncFieldToDb(context, scannedSchemaLabel, scannedField.getLabel());
            if (nextFieldEntity != null) {
                result.add(nextFieldEntity);
            }
        }

        for (var nextFieldEntity : result) {
            context.getNextFieldSchemaIndex().put(scannedSchemaLabel + ":" + nextFieldEntity.getLabel(),
                    nextFieldEntity);
            var schemas = nextFieldEntity.getSchemas();
            if (schemas == null) {
                schemas = new ArrayList<>();
            }
            schemas.add(scannedSchemaLabel);
            nextFieldEntity.setSchemas(schemas);
        }

        context.getNextFieldEntitiesByResourceDesc().put(resourceDesc, result);

        return result;
    }

    private Field syncFieldToDb(SyncDbContext context, String scannedSchemaLabel, String scannedFieldLabel) {
        var classIndex = ResourceRegistry.getClassindex();
        var resourceClass = classIndex.get(scannedSchemaLabel);
        var resourceDesc = ResourceRegistry.getResourceDescs().get(scannedSchemaLabel);
        var scannedField = resourceDesc.getField(scannedFieldLabel);
        var declaringClass = scannedField.getField().getDeclaringClass();
        if (declaringClass.equals(Resource.class) || declaringClass.equals(Schema.class)) {
            return null;
        }
        var declaringSchemaLabel = ResourceRegistry.getSchemaByClass(declaringClass);


        if (declaringSchemaLabel.equals(scannedSchemaLabel)) {
            var fieldEntity = context.getFieldSchemaIndex().get(scannedSchemaLabel + ":" + scannedField.getLabel());
            var nextFieldEntity = context.getNextFieldSchemaIndex().get(scannedSchemaLabel + ":" + scannedField.getLabel());
            if (nextFieldEntity != null) {
                return nextFieldEntity;
            }
            nextFieldEntity = Field.builder()
                    .label(scannedField.getLabel())
                    .type(scannedField.getType())
                    .owner(scannedSchemaLabel)
                    .multiple(scannedField.isMultiple())
                    .required(false)
                    .editable(false)
                    .priv(false)
                    .scannedField(scannedField)
                    .build();
            if (fieldEntity != null) {
                nextFieldEntity.setId(fieldEntity.getId());
                if (!nextFieldEntity.equals(fieldEntity)) {
                    fieldRepository.update(nextFieldEntity);
                }
            } else {
                nextFieldEntity = fieldRepository.create(nextFieldEntity);
            }
            scannedField.setId(nextFieldEntity.getId());
            context.getNextFieldIndex().put(nextFieldEntity.getId(), nextFieldEntity);
            context.getNextFieldSchemaIndex().put(scannedSchemaLabel + ":" + scannedField.getLabel(),
                    nextFieldEntity);
            return nextFieldEntity;
        } else {
            var ancestorSchema = ResourceRegistry.getSchemaIndex().get(declaringClass);
            if (ancestorSchema == null) {
                throw new UnexpectedException("INVALID_FIELD_DECLARATION",
                        "The field " + scannedField.getLabel() + " in class " + resourceClass.getCanonicalName()
                                + " is declared in an class not annoted with @Entity or @ApiRequest ("
                                + declaringClass.getCanonicalName() + ")");
            }
            return syncFieldToDb(context, ancestorSchema, scannedFieldLabel);
        }
    }

    private void syncFieldsPeer(SyncDbContext context) {

        for (var nextFieldEntity : context.getNextFieldIndex().values()) {
            var fieldEntity = context.getFieldIndex().get(nextFieldEntity.getId());
            var scannedField = nextFieldEntity.getScannedField();
            if (scannedField.getPeer() == null) {
                if (fieldEntity != null && fieldEntity.getPeerId() != null) {
                    nextFieldEntity.setPeerId(null);
                    fieldRepository.updatePeer(nextFieldEntity);
                }
            } else {
                var peerFieldEntity = context.getNextFieldSchemaIndex()
                        .get(scannedField.getType() + ":" + scannedField.getPeer());
                if (peerFieldEntity == null) {
                    throw new UnexpectedException("INVALID_PEER_FIELD",
                            "The field " + scannedField.getLabel() + " references a non existing peer "
                                    + scannedField.getType() + ":" + scannedField.getPeer());
                }
                if (fieldEntity == null || fieldEntity.getPeerId() != peerFieldEntity.getId()) {
                    nextFieldEntity.setPeerId(peerFieldEntity.getId());
                    fieldRepository.updatePeer(nextFieldEntity);
                }
            }
        }
    }

    private void linkFieldsToSchemas(SyncDbContext context) {
        for (var nextFieldEntity : context.getNextFieldIndex().values()) {
            var fieldEntity = context.getFieldIndex().get(nextFieldEntity.getId());
            for (var nextSchema : nextFieldEntity.getSchemas()) {
                if (fieldEntity == null || !fieldEntity.getSchemas().contains(nextSchema)) {

                    fieldSchemaRepository.create(
                            Ref.builder()
                                    .id(nextFieldEntity.getId())
                                    .build(),
                            Ref.builder()
                                    .id(cache.getSchemaId(nextSchema))
                                    .build());
                } else {
                    for (var schema : fieldEntity.getSchemas()) {
                        if (!nextFieldEntity.getSchemas().contains(schema)) {
                            fieldSchemaRepository.delete(
                                    Ref.builder()
                                            .id(nextFieldEntity.getId())
                                            .build(),
                                    Ref.builder()
                                            .id(cache.getSchemaId(schema))
                                            .build());
                        }
                    }
                }
            }
        }

    }

    private void removeFieldsFromDb(SyncDbContext context) {
        for (var fieldId : context.getFieldIndex().keySet()) {
            if (!context.getNextFieldIndex().containsKey(fieldId)) {
                fieldRepository.delete(fieldId);
            }
        }
    }
}
