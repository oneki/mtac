package net.oneki.mtac.framework.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import net.oneki.mtac.framework.util.security.PasswordUtil;

public class EntityModule extends SimpleModule {

    public EntityModule(ObjectMapper mapper, PasswordUtil passwordUtil) {
        super();
        // setDeserializerModifier(new BeanDeserializerModifier() {
        //     @Override
        //     public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
        //             BeanDescription beanDescription,
        //             JsonDeserializer<?> originalDeserializer) {
                
        //         // if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
        //         //     return originalDeserializer;
        //         // }
                
        //         // return new DbRelationDeserializer(beanDescription, originalDeserializer, mapper);
                
        //         if (Resource.class.isAssignableFrom(beanDescription.getBeanClass())) {
        //             return new DbRelationDeserializer(beanDescription, originalDeserializer, mapper);
        //         } else {
        //             return originalDeserializer;
        //         }
        //     }
        // });

        setSerializerModifier(new EntityToDbSerializerModifier(passwordUtil));
    }

}
