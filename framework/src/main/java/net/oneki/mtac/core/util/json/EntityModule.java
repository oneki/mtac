package net.oneki.mtac.core.util.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;

import net.oneki.mtac.resource.Resource;

public class EntityModule extends SimpleModule {

    public EntityModule(ObjectMapper mapper) {
        super();
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription beanDescription,
                    JsonDeserializer<?> originalDeserializer) {
                
                if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return originalDeserializer;
                }
                
                return new DbRelationDeserializer(beanDescription, originalDeserializer, mapper);

                // if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
                //     return new RelationDeserializer(beanDescription, originalDeserializer);
                // } else {
                //     return originalDeserializer;
                // }
            }
        });

        setSerializerModifier(new EntityToDbSerializerModifier());
    }

}
