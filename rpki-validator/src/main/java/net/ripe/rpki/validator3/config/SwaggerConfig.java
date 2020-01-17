/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.config;

import com.fasterxml.classmate.TypeResolver;

import net.ripe.rpki.validator3.api.InternalApiCall;
import net.ripe.rpki.validator3.api.PublicApiCall;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.Links;
import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.AlternateTypeRule;
import springfox.documentation.schema.AlternateTypeRules;
import springfox.documentation.schema.WildcardType;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import springfox.documentation.service.Tag;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Configuration
@EnableSwagger2
@Import(value = BeanValidatorPluginsConfiguration.class)
public class SwaggerConfig {
    private Docket docketBuilder() {
        final TypeResolver typeResolver = new TypeResolver();

        return new Docket(DocumentationType.SWAGGER_2)
                .alternateTypeRules(AlternateTypeRules.newRule(typeResolver.resolve(Optional.class, URI.class), String.class, AlternateTypeRule.HIGHEST_PRECEDENCE + 1000))
                .alternateTypeRules(AlternateTypeRules.newRule(
                        typeResolver.resolve(Stream.class, WildcardType.class),
                        typeResolver.resolve(List.class, WildcardType.class),
                        AlternateTypeRule.HIGHEST_PRECEDENCE + 1002
                ))
                .directModelSubstitute(URI.class, String.class)
                .directModelSubstitute(Links.class, Object.class)
                .genericModelSubstitutes(Optional.class);
    }

    @Bean
    public Docket mainApi() {
        return docketBuilder()
                .tags(
                    new Tag("BGP preview", "Provides a preview of the likely RPKI valdidity state your routers will associate with BGP announcements."),
                    new Tag("Ignore filters", "Ignore filters (filters that exclude ROAs)"),
                    new Tag("RPKI repositories", "RPKI repositories"),
                    new Tag("RPKI objects", "All valid RPKI objects"),
                    new Tag("Trust Anchors", "Trust Anchors"),
                    new Tag("Validated objects", "Validated objects (rpki-rtr-server API)"),
                    new Tag("VRP export", "Validated ROA Payload export"),
                    new Tag("Whitelist", "Whitelist entries")
                )
                .groupName("Public APIs")
                .select()
                .apis(RequestHandlerSelectors.withClassAnnotation(PublicApiCall.class))
                .paths(PathSelectors.any())
                .build();
    }

    @Bean
    public Docket internalApi() {
        return docketBuilder()
                .groupName("Status and health check APIs")
                .select()
                .apis(RequestHandlerSelectors.withClassAnnotation(InternalApiCall.class))
                .paths(PathSelectors.any())
                .build();
    }
}
