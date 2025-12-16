package net.oneki.mtac.framework.json;



import net.oneki.mtac.model.resource.Resource;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;

public class EntityModule extends SimpleModule {

    public EntityModule(ObjectMapper mapper) {
        super();
        setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription.Supplier beanDescRef, ValueDeserializer<?> originalDeserializer) {
                
                // if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
                //     return originalDeserializer;
                // }
                
                // return new DbRelationDeserializer(beanDescription, originalDeserializer, mapper);
                var beanDescription = beanDescRef.get();
                if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return new DbRelationDeserializer(beanDescription, originalDeserializer, mapper);
                } else {
                    return originalDeserializer;
                }
            }
        });

        setSerializerModifier(new EntityToDbSerializerModifier());
    }

}
