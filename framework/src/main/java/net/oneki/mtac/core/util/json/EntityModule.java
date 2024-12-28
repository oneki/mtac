package net.oneki.mtac.core.util.json;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.module.SimpleModule;

@Component
public class EntityModule extends SimpleModule {
    @Autowired
    public EntityModule() {
        super();
        // setDeserializerModifier(new BeanDeserializerModifier() {
        //     @Override
        //     public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
        //             BeanDescription beanDescription,
        //             JsonDeserializer<?> originalDeserializer) {

        //         if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
        //             return new RelationDeserializer(beanDescription, originalDeserializer);
        //         } else {
        //             return originalDeserializer;
        //         }
        //     }
        // });

        setSerializerModifier(new EntityToDbSerializerModifier());
    }

}
