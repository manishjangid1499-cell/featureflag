package com.featureflag.flag_service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.util.Locale;
import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientOptionsBuilderCustomizer redisClientOptionsCustomizer() {
        return options -> options.autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .requestQueueSize(256);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(new FlagConfigKeySerializer());
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /** MySQL flag identity is case insensitive; stored spelling still seeds existing rollouts. */
    static final class FlagConfigKeySerializer extends StringRedisSerializer {
        private static final String PREFIX = "flag:config:";
        private static final String VERSIONED_PREFIX = "flag:config:v2:";

        @Override
        public byte[] serialize(String key) {
            if (key != null && key.startsWith(PREFIX)) {
                String suffix = key.substring(key.startsWith(VERSIONED_PREFIX)
                        ? VERSIONED_PREFIX.length() : PREFIX.length());
                // A new namespace prevents reading stale pre-upgrade casing aliases.
                key = VERSIONED_PREFIX + suffix.toLowerCase(Locale.ROOT);
            }
            return super.serialize(key);
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
