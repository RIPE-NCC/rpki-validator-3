package net.ripe.rpki.validator3.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.ripe.rpki.validator3.api.Api;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.jackson.JsonObjectDeserializer;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.*;

@Configuration
public class ApiConfig implements WebMvcConfigurer {

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
            jacksonObjectMapperBuilder.deserializerByType(Links.class, new JsonObjectDeserializer<Links>() {
                @Override
                protected Links deserializeObject(JsonParser jsonParser, DeserializationContext context, ObjectCodec codec, JsonNode tree) throws IOException {
                    Iterator<Map.Entry<String, JsonNode>> iterator = tree.fields();
                    List<Link> links = new ArrayList<>();
                    while (iterator.hasNext()) {
                        Map.Entry<String, JsonNode> field = iterator.next();
                        links.add(new Link(field.getValue().asText(), field.getKey()));
                    }
                    return new Links(links);
                }
            });
        };
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.valueOf("text/json")));
        converters.add(converter);
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            .favorPathExtension(false)
            .defaultContentType(MediaType.valueOf(Api.API_MIME_TYPE));
    }
}
