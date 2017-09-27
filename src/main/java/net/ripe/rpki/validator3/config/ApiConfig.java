package net.ripe.rpki.validator3.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.io.IOException;

@Configuration
public class ApiConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer includeNonNullOnly() {
        return (jacksonObjectMapperBuilder) -> {
            jacksonObjectMapperBuilder.serializationInclusion(JsonInclude.Include.NON_NULL);
        };
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizeLinksRendering() {
        return (jacksonObjectMapperBuilder) -> {
            jacksonObjectMapperBuilder.serializerByType(Links.class, new JsonObjectSerializer<Links>() {
                @Override
                protected void serializeObject(Links value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                    for (Link link: value) {
                        jgen.writeStringField(link.getRel(), link.getHref());
                    }
                }
            });
        };
    }
}
