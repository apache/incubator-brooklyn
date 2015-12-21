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
package org.apache.brooklyn.util.core;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.BrooklynMavenArtifacts;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.maven.MavenArtifact;
import org.apache.brooklyn.util.maven.MavenRetriever;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class BrooklynMavenArtifactsTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynMavenArtifactsTest.class);
    
    @Test(groups="Integration")
    public void testUtilsCommon() {
        ResourceUtils.create(this).checkUrlExists(BrooklynMavenArtifacts.localUrlForJar("brooklyn-utils-common"));
    }

    @Test(groups="Integration")
    public void testExampleWar() {
        String url = BrooklynMavenArtifacts.localUrl("example", "brooklyn-example-hello-world-sql-webapp", "war");
        ResourceUtils.create(this).checkUrlExists(url);
        log.info("found example war at: "+url);
    }

    @Test(groups="Integration")
    // runs without internet but doesn't assert what it should, and can take a long time, so integration
    public void testBadExampleWar() {
        String url = BrooklynMavenArtifacts.localUrl("example", "brooklyn-example-GOODBYE-world-sql-webapp", "war");
        Assert.assertFalse(ResourceUtils.create(this).doesUrlExist(url), "should not exist: "+url);
    }

    public void testHostedIsHttp() {
        String common = BrooklynMavenArtifacts.hostedUrlForJar("brooklyn-utils-common");
        log.info("online should be at: "+common);
        Assert.assertTrue(common.startsWith("http"));
    }

    @Test(groups="Integration")
    public void testHistoricHosted() {
        // NB: this should be a version known to be up at sonatype or maven central, NOT necessarily the current version!
        String snapshot = MavenRetriever.hostedUrl(MavenArtifact.fromCoordinate("org.apache.brooklyn:brooklyn-utils-common:jar:0.7.0-SNAPSHOT"));
        log.info("Sample snapshot URL is: "+snapshot);
        checkValidArchive(snapshot);
        ResourceUtils.create(this).checkUrlExists(snapshot);
        
        // NB: this should be a version known to be up at sonatype or maven central, NOT necessarily the current version!
        String release = MavenRetriever.hostedUrl(MavenArtifact.fromCoordinate("io.brooklyn:brooklyn-utils-common:jar:0.6.0"));
        log.info("Sample release URL is: "+release);
        checkValidArchive(release);
    }

    private void checkValidArchive(final String url) {
        // Note have seen response code 500 from repository.apache.org, for
        //   https://repository.apache.org/service/local/artifact/maven/redirect?r=snapshots&v=0.7.0-SNAPSHOT&g=org.apache.brooklyn&a=brooklyn-utils-common&e=jar
        // Therefore willing to retry, rather than failing immediately.
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                try {
                    byte[] bytes = Streams.readFully(ResourceUtils.create(this).getResourceFromUrl(url));
                    // confirm this follow redirects!
                    Assert.assertTrue(bytes.length > 100*1000, "download of "+url+" is suspect ("+Strings.makeSizeString(bytes.length)+")");
                    // (could also check it is a zip etc)
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }});
    }

}
