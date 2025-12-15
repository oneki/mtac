package net.oneki.mtac.core.util.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.json.RelationDeserializer;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.core.util.json.view.Internal;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.DefaultResourceService;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

@Component
public class DtoModule extends SimpleModule {
    private final static Set<String> skipFields = Set.of("id", "pub", "acl", "link", "s");

    // private final static Set<String> metadataFields = Set.of("createdAt",
    // "updatedAt", "createdBy", "updatedBy", "urn");
    // private final static MetadataNameTransformer metadataNameTransformer = new
    // MetadataNameTransformer();
    @Autowired
    public DtoModule(DefaultResourceService resourceService) {
        super();
        setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
                var beanDescription = beanDescRef.get();
                if (UpsertRequest.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return new DtoDeserializer(deserializer, beanDescription, resourceService);
                } else  if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return new RelationDeserializer(beanDescription, deserializer);
                } else {
                    return deserializer;
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

        setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                    BeanDescription.Supplier beanDescRef, List<BeanPropertyWriter> beanProperties) {
                var beanDesc = beanDescRef.get();
                if (Resource.class.isAssignableFrom(beanDesc.getBeanClass()) ||
                        Ref.class.isAssignableFrom(beanDesc.getBeanClass())) {
                    var result = new ArrayList<BeanPropertyWriter>();
                    var resourceDesc = ResourceRegistry.getResourceDesc(beanDesc.getBeanClass());
                    if (resourceDesc == null) {
                        System.out.println("ResourceDesc not found for " + beanDesc.getBeanClass().getSimpleName());
                    }
                    for (BeanPropertyWriter writer : beanProperties) {
                        var field = resourceDesc.getField(writer.getName());
                        if (field != null && field.isSecret()) {
                            continue;
                        }
                        if (skipFields.contains(writer.getName())
                                || JsonUtil.hasView(writer.getViews(), Internal.class)) {
                            continue;
                            // } else if (metadataFields.contains(writer.getName())) {
                            // result.add(writer.rename(metadataNameTransformer));
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
