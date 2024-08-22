package net.oneki.mtac.util.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import net.oneki.mtac.model.api.UpsertRequest;
import net.oneki.mtac.model.entity.Ref;
import net.oneki.mtac.model.entity.ResourceEntity;
import net.oneki.mtac.service.ResourceService;

@Component
public class UpsertRequestModule extends SimpleModule {
    private final static Set<String> skipFields = Set.of("id", "schemaId", "tenantId", "pub", "acl", "link", "label", "tenant", "schema");
    private final static Set<String> metadataFields = Set.of("createdAt", "updatedAt", "createdBy", "updatedBy", "urn");
    private final static MetadataNameTransformer metadataNameTransformer = new MetadataNameTransformer();
    @Autowired
    public UpsertRequestModule(ResourceService resourceService) {
        super();
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription beanDescription,
                    JsonDeserializer<?> originalDeserializer) {

                if (UpsertRequest.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return new UpsertRequestDeserializer(originalDeserializer, beanDescription, resourceService);
                } else if (ResourceEntity.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return new RelationDeserializer(beanDescription, originalDeserializer);
                } else {
                    return originalDeserializer;
                }
            }

            // @Override
            // public KeyDeserializer modifyKeyDeserializer(DeserializationConfig config,
            // JavaType type, KeyDeserializer deserializer) {
            // if (type.getRawClass().isAssignableFrom(ResourceEntity.class)) {
            // return new RelationKeyDeserializer(type);
            // }
            // return deserializer;
            // }
        });

        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                    BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {

                if (ResourceEntity.class.isAssignableFrom(beanDesc.getBeanClass()) ||
                        Ref.class.isAssignableFrom(beanDesc.getBeanClass())) {
                    var result = new ArrayList<BeanPropertyWriter>();
                    for (BeanPropertyWriter writer : beanProperties) {
                        if (skipFields.contains(writer.getName())) {
                            continue;
                        } else if (metadataFields.contains(writer.getName())) {
                            result.add(writer.rename(metadataNameTransformer));
                        } else {
                            result.add(writer);
                        }
                    }
                    result.sort((a, b) -> a.getName().compareTo(b.getName()));
                    return result;
                }
                return beanProperties;
            }
        });
    }

}
