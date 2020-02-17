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

import net.ripe.rpki.validator3.storage.XodusInitialisationException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.Banner;
import org.springframework.boot.actuate.autoconfigure.cloudfoundry.reactive.ReactiveCloudFoundryActuatorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.cloudfoundry.servlet.CloudFoundryActuatorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.flyway.FlywayEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.jolokia.JolokiaEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.liquibase.LiquibaseEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.amqp.RabbitMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.cache.CacheMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.atlas.AtlasMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.datadog.DatadogMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.ganglia.GangliaMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.graphite.GraphiteMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.influx.InfluxMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.jmx.JmxMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.newrelic.NewRelicMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.signalfx.SignalFxMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.statsd.StatsdMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.wavefront.WavefrontMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.jdbc.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.web.reactive.WebFluxMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.session.SessionsEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.reactive.ReactiveManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.ldap.LdapRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.data.solr.SolrRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastJpaDependencyAutoConfiguration;
import org.springframework.boot.autoconfigure.influx.InfluxDbAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JndiDataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jersey.JerseyAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.JndiConnectionFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.jsonb.JsonbAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.sendgrid.SendGridAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.autoconfigure.webservices.WebServicesAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.reactive.WebSocketReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.BindException;
import java.util.Locale;

@SpringBootApplication(exclude = {
        AopAutoConfiguration.class,
        ArtemisAutoConfiguration.class,
        AtlasMetricsExportAutoConfiguration.class,
        BatchAutoConfiguration.class,
        CacheAutoConfiguration.class,
        CacheMetricsAutoConfiguration.class,
        CassandraDataAutoConfiguration.class,
        CassandraReactiveDataAutoConfiguration.class,
        CassandraReactiveRepositoriesAutoConfiguration.class,
        CassandraRepositoriesAutoConfiguration.class,
        CloudFoundryActuatorAutoConfiguration.class,
        CouchbaseAutoConfiguration.class,
        CouchbaseDataAutoConfiguration.class,
        CouchbaseReactiveDataAutoConfiguration.class,
        CouchbaseReactiveRepositoriesAutoConfiguration.class,
        CouchbaseRepositoriesAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourcePoolMetricsAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        DatadogMetricsExportAutoConfiguration.class,
        ElasticsearchAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class,
        EmbeddedLdapAutoConfiguration.class,
        EmbeddedMongoAutoConfiguration.class,
        ErrorWebFluxAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        FlywayEndpointAutoConfiguration.class,
        FreeMarkerAutoConfiguration.class,
        GangliaMetricsExportAutoConfiguration.class,
        GraphiteMetricsExportAutoConfiguration.class,
        GroovyTemplateAutoConfiguration.class,
        H2ConsoleAutoConfiguration.class,
        HazelcastAutoConfiguration.class,
        HazelcastJpaDependencyAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        HttpHandlerAutoConfiguration.class,
        InfluxDbAutoConfiguration.class,
        InfluxMetricsExportAutoConfiguration.class,
        IntegrationAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        JerseyAutoConfiguration.class,
        JmsAutoConfiguration.class,
        JmxMetricsExportAutoConfiguration.class,
        JndiConnectionFactoryAutoConfiguration.class,
        JndiDataSourceAutoConfiguration.class,
        JolokiaEndpointAutoConfiguration.class,
        JooqAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        JsonbAutoConfiguration.class,
        JtaAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        LdapAutoConfiguration.class,
        LdapRepositoriesAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,
        LiquibaseEndpointAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        MailSenderValidatorAutoConfiguration.class,
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoReactiveAutoConfiguration.class,
        MongoReactiveDataAutoConfiguration.class,
        MongoReactiveRepositoriesAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        MustacheAutoConfiguration.class,
        Neo4jDataAutoConfiguration.class,
        Neo4jRepositoriesAutoConfiguration.class,
        NewRelicMetricsExportAutoConfiguration.class,
        RabbitAutoConfiguration.class,
        RabbitMetricsAutoConfiguration.class,
        ReactiveCloudFoundryActuatorAutoConfiguration.class,
        ReactiveManagementContextAutoConfiguration.class,
        ReactiveSecurityAutoConfiguration.class,
        ReactiveUserDetailsServiceAutoConfiguration.class,
        ReactiveWebServerFactoryAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisReactiveAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        RepositoryRestMvcAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        SendGridAutoConfiguration.class,
        SessionAutoConfiguration.class,
        SessionsEndpointAutoConfiguration.class,
        SignalFxMetricsExportAutoConfiguration.class,
        SolrAutoConfiguration.class,
        SolrRepositoriesAutoConfiguration.class,
        SpringDataWebAutoConfiguration.class,
        StatsdMetricsExportAutoConfiguration.class,
        ThymeleafAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        WavefrontMetricsExportAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        WebFluxAutoConfiguration.class,
        WebFluxMetricsAutoConfiguration.class,
        WebServicesAutoConfiguration.class,
        WebSocketMessagingAutoConfiguration.class,
        WebSocketReactiveAutoConfiguration.class,
        WebSocketServletAutoConfiguration.class,
        XADataSourceAutoConfiguration.class,
})
public class Validator3Application {

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        try {
            new SpringApplicationBuilder(Validator3Application.class)
                    .bannerMode(Banner.Mode.OFF)
                    .build()
                    .run(args);
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

        boolean xodusError = e instanceof XodusInitialisationException;

        if (bindError) {
            System.err.println("The binding address is already in use by another application.");
            exitPreventingRestart();
        } else if (xodusError) {
            System.err.println("Could not initialise the database, most probably the 'rpki.validator.data.path' setting points to an unavailable directory.");
            exitPreventingRestart();
        } else {
            terminateIfKnownProblem(e.getCause());
        }
    }

    /**
     * Exit code 7 is use by systemd and indicates that restarting
     * the application is not going to solve the problem.
     */
    private static void exitPreventingRestart() {
        System.exit(7);
    }
}
