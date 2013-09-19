package com.acme.sample.brooklyn.sample.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.basic.PortRanges;

/** This example starts one web app on 8080. */
public class SingleWebServerSample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerSample.class);

    public static final String DEFAULT_WAR_URL =
            // can supply any URL -- this loads a stock example from maven central / sonatype
            BrooklynVersion.getBrooklynMavenArtifactUrl("io.brooklyn.example", "brooklyn-example-hello-world-webapp", "war");

    /** Initialize our application. In this case it consists of 
     *  a single JBoss entity, configured to run the WAR above. */
    @Override
    public void init() {
        addChild(EntitySpec.create(JBoss7Server.class)
                .configure(JavaWebAppService.ROOT_WAR, DEFAULT_WAR_URL)
                .configure(Attributes.HTTP_PORT, PortRanges.fromString("8080+")));
    }

}
