/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.MavenUtils.asInProject;

import java.io.File;

import javax.inject.Inject;

import org.apache.karaf.features.BootFinished;
import org.apache.karaf.features.FeaturesService;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

/**
 * Tests the apache-brooklyn karaf runtime assembly.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class AssemblyTest {

    @Inject
    private BundleContext bc;

    @Inject
    protected FeaturesService featuresService;

    /**
     * To make sure the tests run only when the boot features are fully
     * installed
     */
    @Inject
    BootFinished bootFinished;

    @Configuration
    public static Option[] configuration() throws Exception {
        return new Option[]{
            karafDistributionConfiguration()
            .frameworkUrl(brooklynKarafDist())
            .unpackDirectory(new File("target/paxexam/unpack/"))
            .useDeployFolder(false),
            configureConsole().ignoreLocalConsole(),
            logLevel(LogLevel.INFO),
            keepRuntimeFolder(),
            features(karafStandardFeaturesRepository(), "eventadmin"),
            junitBundles()
        };
    }

    private static MavenArtifactUrlReference brooklynKarafDist() {
        return maven()
                .groupId("org.apache.brooklyn")
                .artifactId("apache-brooklyn")
                .type("zip")
                .version(asInProject());
    }

    private static MavenUrlReference karafStandardFeaturesRepository() {
        return maven()
                .groupId("org.apache.karaf.features")
                .artifactId("standard")
                .type("xml")
                .classifier("features")
                .version(asInProject());
    }

    @Test
    public void shouldHaveBundleContext() {
        assertNotNull(bc);
    }

    @Test
    public void checkEventFeature() throws Exception {
        assertTrue(featuresService.isInstalled(featuresService.getFeature("eventadmin")));
    }

    @Test
    public void checkBrooklynCoreFeature() throws Exception {
        featuresService.installFeature("brooklyn-core");
        assertTrue(featuresService.isInstalled(featuresService.getFeature("brooklyn-core")));
    }

}
