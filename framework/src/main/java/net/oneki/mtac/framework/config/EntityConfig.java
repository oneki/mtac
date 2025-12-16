package net.oneki.mtac.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.oneki.mtac.framework.cache.Cache;
import net.oneki.mtac.framework.json.EntityMapper;
import net.oneki.mtac.framework.json.EntityModule;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class EntityConfig {

    @Bean(name = "entityMapper")
    public EntityMapper entityMapper() {
        var builder = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))    
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        var mapper = new EntityMapper(builder);

        builder.addModule(new EntityModule(mapper));
        mapper = new EntityMapper(builder);

        // var mapper = EntityMapper.builder()
        //     .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        //     .serializationInclusion(Include.NON_NULL)
        //     // .addModule(new JavaTimeModule())
        //     // .addModule(entityModule)
        //     // .serializerFactory(BeanSerializerFactory.instance
        //     //     .withSerializerModifier(new EntityToDbSerializerModifier()
        //     // ))
        //     .build();
        // var entityModule = new EntityModule(mapper);
        // // mapper.registerModule(new JavaTimeModule());
        // mapper.registerModule(entityModule);

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
