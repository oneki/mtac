package net.oneki.mtac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.oneki.mtac.core.util.cache.Cache;
import net.oneki.mtac.core.util.json.EntityMapper;
import net.oneki.mtac.core.util.json.EntityModule;

@Configuration
public class EntityConfig {

    @Bean(name = "entityMapper")
    public EntityMapper entityMapper() {

        var mapper = EntityMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .serializationInclusion(Include.NON_NULL)
            // .addModule(new JavaTimeModule())
            // .addModule(entityModule)
            // .serializerFactory(BeanSerializerFactory.instance
            //     .withSerializerModifier(new EntityToDbSerializerModifier()
            // ))
            .build();
        var entityModule = new EntityModule(mapper);
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

    @Bean
    public Cache cache() {
        return new Cache();
    }
}
