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
package net.ripe.rpki.validator3;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.BindException;
import java.util.Locale;

@SpringBootApplication
@EnableScheduling
public class Validator3Application {

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        try {
            SpringApplication application = new SpringApplicationBuilder(Validator3Application.class)
                    .bannerMode(Banner.Mode.OFF)
                    .build();
            application.run(args);
        } catch (Exception e) {
            terminateIfKnownProblem(e);
        }
    }

    private static void terminateIfKnownProblem(Throwable e) {
        if (e == null) {
            return;
        }

        boolean bindError = e instanceof java.net.BindException;
        bindError = bindError || e.getClass().getName().equals("org.springframework.boot.web.embedded.tomcat.ConnectorStartFailedException");

        if (e instanceof BeanCreationException) {
            bindError = bindError || ((BeanCreationException) e).getMostSpecificCause() instanceof BindException;
        }

        if (bindError) {
            System.err.println("The binding address is already in use by another application.");
            System.exit(7);
        } else if (e.getClass().getName().equals("org.h2.jdbc.JdbcSQLException")) {
            if (e.getMessage().contains("Database may be already in use")) {
                System.err.println("The database is locked by another process, check if another instance of the validator is running.");
                System.exit(7);
            } else {
                System.err.println("There is a problem using H2 database.");
                System.exit(7);
            }
        } else {
            terminateIfKnownProblem(e.getCause());
        }
    }
}
