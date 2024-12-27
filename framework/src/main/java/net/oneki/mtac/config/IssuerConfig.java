package net.oneki.mtac.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
public class IssuerConfig {
    @Bean
    @ConfigurationProperties(prefix = "security")
    public IssuersProperties issuersProperties(){
        return new IssuersProperties();
    }

    @Data
    public static class IssuersProperties {
        private Map<String, IssuerProperties> trustedIssuers;

        @Data
        public static class IssuerProperties {
            private String issuerUri;
            private String jwkSetUri;
            private String decoderUri;
            private String jwtDecoderBean;
        }
    }
}