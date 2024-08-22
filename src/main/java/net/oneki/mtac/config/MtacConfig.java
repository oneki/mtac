package net.oneki.mtac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.oneki.mtac.util.json.EntityToDbMapper;
import net.oneki.mtac.util.json.EntityToDbSerializerModifier;
import net.oneki.mtac.util.json.UpsertRequestModule;

@Configuration
@ComponentScan("net.oneki.mtac")
public class MtacConfig {
    @Bean
    @Primary
    public ObjectMapper mapper(UpsertRequestModule upsertRequestModule) {
        return JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .serializationInclusion(Include.NON_NULL)
                .addModule(upsertRequestModule)
                .addModule(new JavaTimeModule())
                .build();
    }

    @Bean(name = "entityToDbMapper")
    public EntityToDbMapper entityToDbMapper() {
        return EntityToDbMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .serializationInclusion(Include.NON_NULL)
            .addModule(new JavaTimeModule())
            .serializerFactory(BeanSerializerFactory.instance
                .withSerializerModifier(new EntityToDbSerializerModifier()
            ))
            .build();
    }
}
