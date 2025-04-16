package net.oneki.mtac.framework.repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.entity.Field;
import net.oneki.mtac.framework.entity.SyncDbContext;
import net.oneki.mtac.framework.introspect.ClassType;
import net.oneki.mtac.model.core.Constants;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.schema.Schema;

@RequiredArgsConstructor
@Component
public class SchemaDbSynchronizer {
    private final ResourceRepository resourceRepository;
    private final FieldRepository fieldRepository;
    private final FieldSchemaRepository fieldSchemaRepository;
    private final SchemaRepository schemaRepository;


    public void syncSchemasToDb() {
        var cache = ResourceRegistry.getCache();
        var classIndex = ResourceRegistry.getClassindex();
        var scannedSchemaLabels = classIndex.keySet();
        var dbSchemaLabels = cache.getSchemas().values().stream().map(Schema::getLabel)
                .collect(Collectors.toSet());

        for (var scannedSchemaLabel : scannedSchemaLabels) {
            syncSchemaToDb(scannedSchemaLabel, dbSchemaLabels);
        }

        for (var dbSchemaLabel : dbSchemaLabels) {
            if (scannedSchemaLabels.contains(dbSchemaLabel)) {
                continue;
            }
            schemaRepository.delete(cache.getSchemaId(dbSchemaLabel));
        }
        fieldRepository.deleteOrphans();
    }

    private void syncSchemaToDb(String scannedSchemaLabel, Set<String> dbSchemaLabels) {
        var cache = ResourceRegistry.getCache();
        var classIndex = ResourceRegistry.getClassindex();
        var schemaIndex = ResourceRegistry.getSchemaIndex();

        var resourceDesc = ResourceRegistry.getResourceDescs().get(scannedSchemaLabel);
        if (resourceDesc.getId() != null || resourceDesc.getClassType() == null || resourceDesc.getClassType() == ClassType.ApiRequest) {
            return;
        }
        var resourceClass = classIndex.get(scannedSchemaLabel);
        var parentSchemaLabels = new HashSet<String>();
        var parentResourceClass = resourceClass.getSuperclass();
        var superSchemaLabel = schemaIndex.get(parentResourceClass);
        if (superSchemaLabel != null) {
            parentSchemaLabels.add(superSchemaLabel);
        }
        var interfaceClasses = resourceClass.getInterfaces();
        for (var interfaceClass : interfaceClasses) {
            var interfaceSchemaLabel = schemaIndex.get(interfaceClass);
            if (interfaceSchemaLabel != null) {
                parentSchemaLabels.add(interfaceSchemaLabel);
            }
        }

        var parents = new ArrayList<Ref>();
        for (var parentSchemaLabel : parentSchemaLabels) {
            syncSchemaToDb(parentSchemaLabel, dbSchemaLabels);
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
                    .tenantId(Constants.TENANT_ROOT_ID)
                    .schemaId(Constants.SCHEMA_SCHEMA_ID)
                    .name(scannedSchemaLabel)
                    .parents(parents)
                    .build());
            cache.addSchema(schemaEntity);
            resourceDesc.setId(schemaEntity.getId());
        } else {
            // compare parents
            var schemaEntity = cache.getSchemaByLabel(scannedSchemaLabel);
            resourceDesc.setId(schemaEntity.getId());
            var parentsInDb = schemaEntity.getParents();
            if (parentsInDb.containsAll(parents) && parents.containsAll(parentsInDb)) {
                return;
            }
            schemaEntity.setParents(parents);
            resourceRepository.update(schemaEntity);
        }        
    }

    public void syncFieldsToDb() {
        var context = new SyncDbContext();
        var scannedSchemaLabels = ResourceRegistry.getClassindex().keySet();
        context.setFieldEntities(fieldRepository.listAllFieldsUnsecure());

        for (var fieldEntity : context.getFieldEntities()) {
            for (var schema : fieldEntity.getSchemas()) {
                context.getDbFieldSchemaIndex().put(schema + ":" + fieldEntity.getLabel(), fieldEntity);
            }
            context.getDbFieldIndex().put(fieldEntity.getId(), fieldEntity);
        }

        for (var scannedSchemaLabel : scannedSchemaLabels) {
            syncFieldsToDb(context, scannedSchemaLabel);
        }
        removeFieldsFromDb(context);
        // linkFieldsToSchemas(context);
        syncFieldsPeer(context);
    }

    private List<Field> syncFieldsToDb(SyncDbContext context, String scannedSchemaLabel) {
        var resourceDescs = ResourceRegistry.getResourceDescs();
        var result = new ArrayList<Field>();
        var resourceDesc = resourceDescs.get(scannedSchemaLabel);
        if (resourceDesc.getClassType() == null || resourceDesc.getClassType() == ClassType.ApiRequest) {
            return result;
        }
        
        if (context.getScannedFieldEntitiesByResourceDesc().containsKey(resourceDesc)) {
            return context.getScannedFieldEntitiesByResourceDesc().get(resourceDesc);
        }

        for (var scannedField : resourceDesc.getFields()) {
            var nextFieldEntity = syncFieldToDb(context, scannedSchemaLabel, scannedField.getLabel());
            if (nextFieldEntity != null) {
                result.add(nextFieldEntity);
            }
        }

        for (var nextFieldEntity : result) {
            context.getScannedFieldSchemaIndex().put(scannedSchemaLabel + ":" + nextFieldEntity.getLabel(),
                    nextFieldEntity);
            var schemas = nextFieldEntity.getSchemas();
            if (schemas == null) {
                schemas = new ArrayList<>();
            }
            var childSchemaLabels = resourceDesc.getChildClasses().stream()
                    .map(childClass -> ResourceRegistry.getSchemaIndex().get(childClass))
                    .toList();

            schemas.add(scannedSchemaLabel);
            schemas.addAll(childSchemaLabels);
            nextFieldEntity.setSchemas(schemas);
        }

        context.getScannedFieldEntitiesByResourceDesc().put(resourceDesc, result);

        return result;
    }

    private Field syncFieldToDb(SyncDbContext context, String scannedSchemaLabel, String scannedFieldLabel) {
        var cache = ResourceRegistry.getCache();
        var classIndex = ResourceRegistry.getClassindex();
        var resourceClass = classIndex.get(scannedSchemaLabel);
        var resourceDesc = ResourceRegistry.getResourceDescs().get(scannedSchemaLabel);
        var scannedField = resourceDesc.getField(scannedFieldLabel);
        var declaringClass = scannedField.getOwnerClass() != null ? scannedField.getOwnerClass() : scannedField.getField().getDeclaringClass();
        if (declaringClass.equals(Resource.class) || declaringClass.equals(Schema.class)) {
            return null;
        }
        if (scannedField.getOwnerClass() != null && !scannedField.getOwnerClass().equals(resourceClass)) {
            return null;
        }
        var declaringSchemaLabel = ResourceRegistry.getSchemaByClass(declaringClass);

        if (declaringSchemaLabel.equals(scannedSchemaLabel)) {
            var fieldEntity = context.getDbFieldSchemaIndex().get(scannedSchemaLabel + ":" + scannedField.getLabel());
            var nextFieldEntity = context.getScannedFieldSchemaIndex().get(scannedSchemaLabel + ":" + scannedField.getLabel());
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
            context.getScannedFieldIndex().put(nextFieldEntity.getId(), nextFieldEntity);
            context.getScannedFieldSchemaIndex().put(scannedSchemaLabel + ":" + scannedField.getLabel(),
                    nextFieldEntity);
            var nextFieldSchemas = scannedField.getImpClasses().stream()
                .map(implClass -> ResourceRegistry.getSchemaByClass(implClass))
                .collect(Collectors.toSet());
            for (var nextFieldSchema : nextFieldSchemas) {
                if (fieldEntity == null || fieldEntity.getSchemas() == null
                        || !fieldEntity.getSchemas().contains(nextFieldSchema)) {
                    fieldSchemaRepository.create(
                            Ref.builder()
                                    .id(nextFieldEntity.getId())
                                    .build(),
                            Ref.builder()
                                    .id(cache.getSchemaId(nextFieldSchema))
                                    .build());
                }  
                
            }
            if (fieldEntity != null && fieldEntity.getSchemas() != null) {
                for (var schema : fieldEntity.getSchemas()) {
                    if (!nextFieldSchemas.contains(schema)) {
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

        for (var nextFieldEntity : context.getScannedFieldIndex().values()) {
            var fieldEntity = context.getDbFieldIndex().get(nextFieldEntity.getId());
            var scannedField = nextFieldEntity.getScannedField();
            if (scannedField.getPeer() == null) {
                if (fieldEntity != null && fieldEntity.getPeerId() != null) {
                    nextFieldEntity.setPeerId(null);
                    fieldRepository.updatePeer(nextFieldEntity);
                }
            } else {
                var peerFieldEntity = context.getScannedFieldSchemaIndex()
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

    // private void linkFieldsToSchemas(SyncDbContext context) {
    //     for (var nextFieldEntity : context.getScannedFieldIndex().values()) {
    //         var fieldEntity = context.getDbFieldIndex().get(nextFieldEntity.getId());
    //         for (var nextSchema : nextFieldEntity.getSchemas()) {
    //             if (fieldEntity == null || !fieldEntity.getSchemas().contains(nextSchema)) {

    //                 fieldSchemaRepository.create(
    //                         Ref.builder()
    //                                 .id(nextFieldEntity.getId())
    //                                 .build(),
    //                         Ref.builder()
    //                                 .id(cache.getSchemaId(nextSchema))
    //                                 .build());
    //             } else {
    //                 for (var schema : fieldEntity.getSchemas()) {
    //                     if (!nextFieldEntity.getSchemas().contains(schema)) {
    //                         fieldSchemaRepository.delete(
    //                                 Ref.builder()
    //                                         .id(nextFieldEntity.getId())
    //                                         .build(),
    //                                 Ref.builder()
    //                                         .id(cache.getSchemaId(schema))
    //                                         .build());
    //                     }
    //                 }
    //             }
    //         }
    //     }

    // }

    private void removeFieldsFromDb(SyncDbContext context) {
        for (var fieldId : context.getDbFieldIndex().keySet()) {
            if (!context.getScannedFieldIndex().containsKey(fieldId)) {
                fieldRepository.delete(fieldId);
            }
        }
    }
}
