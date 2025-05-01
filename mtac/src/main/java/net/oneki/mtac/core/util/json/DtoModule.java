package net.oneki.mtac.core.util.json;

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

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.json.RelationDeserializer;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.core.util.json.view.Internal;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.DefaultResourceService;

@Component
public class DtoModule extends SimpleModule {
    private final static Set<String> skipFields = Set.of("id", "pub", "acl", "link", "$l", "$t", "$s");
    // private final static Set<String> metadataFields = Set.of("createdAt", "updatedAt", "createdBy", "updatedBy", "urn");
    // private final static MetadataNameTransformer metadataNameTransformer = new MetadataNameTransformer();
    @Autowired
    public DtoModule(DefaultResourceService resourceService) {
        super();
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription beanDescription,
                    JsonDeserializer<?> originalDeserializer) {

                if (UpsertRequest.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return new DtoDeserializer(originalDeserializer, beanDescription, resourceService);
                } else if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
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

                if (Resource.class.isAssignableFrom(beanDesc.getBeanClass()) ||
                        Ref.class.isAssignableFrom(beanDesc.getBeanClass())) {
                    var result = new ArrayList<BeanPropertyWriter>();
                    var resourceDesc = ResourceRegistry.getResourceDesc(beanDesc.getBeanClass());
                    for (BeanPropertyWriter writer : beanProperties) {
                        var field = resourceDesc.getField(writer.getName());
                        if (field != null && field.isSecret()) {
                            continue;
                        } 
                        if (skipFields.contains(writer.getName()) || JsonUtil.hasView(writer.getViews(), Internal.class)) {
                            continue;
                        // } else if (metadataFields.contains(writer.getName())) {
                        //     result.add(writer.rename(metadataNameTransformer));
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
