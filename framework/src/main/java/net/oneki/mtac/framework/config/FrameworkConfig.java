package net.oneki.mtac.framework.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScans({
    @ComponentScan("net.oneki.mtac.framework.repository"),
    @ComponentScan("net.oneki.mtac.framework.cache"),
    @ComponentScan("net.oneki.mtac.framework.service")
})
public class FrameworkConfig {
    
}
