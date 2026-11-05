package com.example.flagship.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TimeConfig {

    /**
     * UTC clock ticking in whole microseconds, matching PostgreSQL's timestamp precision. Without
     * this, a create response could carry nanoseconds that a later read of the same row cannot return.
     */
    @Bean
    Clock clock() { return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)); }
}
