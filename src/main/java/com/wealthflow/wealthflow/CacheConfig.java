package com.wealthflow.wealthflow;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // Uses Spring Boot's default ConcurrentMapCacheManager
    // No custom configuration needed for basic caching
}
