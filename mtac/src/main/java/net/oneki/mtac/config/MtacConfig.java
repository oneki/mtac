package net.oneki.mtac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.annotation.JsonInclude;

import net.oneki.mtac.core.util.json.DtoModule;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ComponentScan("net.oneki.mtac")
public class MtacConfig {
    @Bean
    @Primary
    public JsonMapper mapper(DtoModule upsertRequestModule) {
        var mapper =  JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .addModule(upsertRequestModule)
            .build();
    
        
        return mapper;
    }

    @Bean
    public SecurityContext securityContext() {
        return new SecurityContext();
    }

    @Bean
    @ConfigurationProperties(prefix = "mtac")
    public MtacProperties mtacProperties() {
        return new MtacProperties();
    }

}
