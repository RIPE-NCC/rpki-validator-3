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

import net.ripe.rpki.validator3.api.StaticContentFixServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ServletConfig implements WebMvcConfigurer {
    @Bean
    public ServletRegistrationBean<StaticContentFixServlet> provisioningServlet() {
        return new ServletRegistrationBean<>(new StaticContentFixServlet(), "/index.html");
    }

    /**
     * Mapping for Single Page Application (SPA).
     * When a browser requests the path of a client-side route, return index.js. The Angular application will render
     * the correct client side route. A number of paths are blacklisted from this (internal) redirect.
     *
     * From https://stackoverflow.com/questions/39331929/spring-catch-all-route-for-index-html/42998817#42998817
     *
     * Spring's route regex support (intended to capture variables from the path) is used here to white/blacklist
     * paths that should not go to the SPA.
     *
     * The regex:
     *   * Ignores names ending with an extension ([^\\.]+)
     *   * <pre>{x:<regex>}</pre> syntax: Spring parses this part of the url path as a regex, captures it so it can be
           used by a @PathParam with the given name (e.g. <pre>x</pre>).
     *   * Blacklists a number of tokens, using:
     *     * <pre>(?!<regex>)</pre>: Negative match.
     *     * <pre>(?:<regex>)</pre>: Non-capturing group.
     *       * Needed here for correct behaviour.
     *
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        final String blackListedPaths = String.join("|", "api", "cache", "clients", "actuator", "metrics");

        // Root
        registry.addViewController("/")
                .setViewName("forward:/index.html");
        registry.addViewController("/metrics")
                .setViewName("forward:/actuator/prometheus");
        // Single directory level
        registry.addViewController(String.format("/{x:(?!(?:%s)$)[^\\.]+}", blackListedPaths))
                .setViewName("forward:/index.html");
        // Multi-level directory path (/first/second/.../n-th)
        registry.addViewController(String.format("/{x:^(?!(?:%s)$).*$}/**/{y:[^\\.]*}", blackListedPaths))
                .setViewName("forward:/index.html");
    }
}
