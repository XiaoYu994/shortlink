package com.xhy.shortlink.project.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* Java 的 Long 类型能存 19 位数字，但前端 JavaScript 的 Number 类型只能精确到 16 位。
*  对 Long 类型进行转化为 String 类型的操作
* */
@Configuration
public class JacksonConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 把 Long 类型序列化为 String
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            // 把 long 基本类型序列化为 String (可选，根据需求)
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            // 如果你也使用了 BigInteger，也可以加上
            // builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
        };
    }
}
