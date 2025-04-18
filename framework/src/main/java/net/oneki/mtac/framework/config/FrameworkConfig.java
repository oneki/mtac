package net.oneki.mtac.framework.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import net.oneki.mtac.framework.util.security.PasswordUtil;

@Configuration
@ComponentScans({
    @ComponentScan("net.oneki.mtac.framework.repository"),
    @ComponentScan("net.oneki.mtac.framework.cache"),
    @ComponentScan("net.oneki.mtac.framework.service")
})
public class FrameworkConfig {

    @Bean
	public StringEncryptor stringEncryptor(@Value("${mtac.security.encryption.key}") String key) {
		PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
		SimpleStringPBEConfig config = new SimpleStringPBEConfig();
		config.setPassword(key);
		config.setAlgorithm("PBEWithMD5AndDES");
		config.setKeyObtentionIterations("1000");
		config.setPoolSize("1");
		config.setProviderName("SunJCE");
		config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
		config.setStringOutputType("base64");
		encryptor.setConfig(config);
		return encryptor;
	}

    @Bean
    public PasswordUtil passwordUtil(PasswordEncoder passwordEncoder, StringEncryptor stringEncryptor) {
        return new PasswordUtil(passwordEncoder, stringEncryptor);
    }
}
