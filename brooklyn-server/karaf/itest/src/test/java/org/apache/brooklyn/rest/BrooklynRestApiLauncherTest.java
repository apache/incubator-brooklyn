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
package org.apache.brooklyn.rest;

import java.io.File;
import java.util.concurrent.Callable;
import org.apache.brooklyn.AssemblyTest;

import org.apache.brooklyn.rest.util.BrooklynRestResourceUtilsTest.SampleNoOpApplication;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.http.HttpStatus;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
@Ignore // TODO: re-enable after brooklyn is properly initialized within the OSGI environment
public class BrooklynRestApiLauncherTest extends BrooklynRestApiLauncherTestFixture {

    private static final String HTTP_PORT = "9998";

    @Configuration
    public static Option[] configuration() throws Exception {
        return new Option[]{
            karafDistributionConfiguration()
            .frameworkUrl(AssemblyTest.brooklynKarafDist())
            .unpackDirectory(new File("target/paxexam/unpack/"))
            .useDeployFolder(false),
            editConfigurationFilePut("etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", HTTP_PORT),
            configureConsole().ignoreLocalConsole(),
            logLevel(LogLevelOption.LogLevel.INFO),
            keepRuntimeFolder(),
            features(AssemblyTest.karafStandardFeaturesRepository(), "eventadmin"),
            junitBundles()
        };
    }

    @Test
    public void testStart() throws Exception {
        final String rootUrl = "http://localhost:" + HTTP_PORT;
        final String appsUrl = rootUrl + "/v1/catalog/applications";
        int code = Asserts.succeedsEventually(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                int code = HttpTool.getHttpStatusCode(appsUrl);
                if (code == HttpStatus.SC_FORBIDDEN
                        || code == HttpStatus.SC_NOT_FOUND) { // wait for service up
                    throw new RuntimeException("Retry request");
                } else {
                    return code;
                }
            }
        });
        HttpAsserts.assertHealthyStatusCode(code);
        HttpAsserts.assertContentContainsText(appsUrl, SampleNoOpApplication.class.getSimpleName());
    }

//    private BrooklynRestApiLauncher baseLauncher() {
//        return BrooklynRestApiLauncher.launcher()
//                .securityProvider(AnyoneSecurityProvider.class)
//                .forceUseOfDefaultCatalogWithJavaClassPath(true);
//    }
}
