package org.example.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class ApplicationConfig {
    @Value("${application.poolSize}")
    private int poolSize;
}
