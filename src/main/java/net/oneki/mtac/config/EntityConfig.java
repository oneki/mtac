package net.oneki.mtac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.oneki.mtac.util.json.EntityMapper;
import net.oneki.mtac.util.json.EntityModule;
import net.oneki.mtac.util.json.EntityToDbSerializerModifier;

@Configuration
public class EntityConfig {
    @Bean(name = "entityMapper")
    public EntityMapper entityMapper(EntityModule entityModule) {
        var mapper = EntityMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .serializationInclusion(Include.NON_NULL)
            // .addModule(new JavaTimeModule())
            // .addModule(entityModule)
            // .serializerFactory(BeanSerializerFactory.instance
            //     .withSerializerModifier(new EntityToDbSerializerModifier()
            // ))
            .build();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(entityModule);
        return mapper;
        // return JsonMapper.builder()
        //     .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        //     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        //     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        //     .serializationInclusion(Include.NON_NULL)
        //     .addModule(new JavaTimeModule())
        //     .serializerFactory(BeanSerializerFactory.instance
        //                 .withSerializerModifier(new EntityToDbSerializerModifier()))
        // .build();
    }
}
