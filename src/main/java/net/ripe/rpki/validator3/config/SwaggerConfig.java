package net.ripe.rpki.validator3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.Links;
import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.AlternateTypeRule;
import springfox.documentation.schema.AlternateTypeRules;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.net.URI;
import java.util.Optional;

@Configuration
@EnableSwagger2
@Import(value = BeanValidatorPluginsConfiguration.class)
public class SwaggerConfig {
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
            .alternateTypeRules(AlternateTypeRules.newRule(org.apache.commons.lang3.reflect.TypeUtils.parameterize(Optional.class, URI.class), String.class, AlternateTypeRule.HIGHEST_PRECEDENCE + 1000))
            .alternateTypeRules(AlternateTypeRules.newRule(org.apache.commons.lang3.reflect.TypeUtils.parameterize(Optional.class, Links.class), Object.class, AlternateTypeRule.HIGHEST_PRECEDENCE + 1000))
            .directModelSubstitute(URI.class, String.class)
            .directModelSubstitute(Links.class, Object.class)
            .genericModelSubstitutes(Optional.class)
            .select()
            .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.any())
            .build();
    }
}
