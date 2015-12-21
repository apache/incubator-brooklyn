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
package org.apache.brooklyn.util.maven;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.maven.MavenArtifact;
import org.apache.brooklyn.util.maven.MavenArtifactTest;
import org.apache.brooklyn.util.maven.MavenRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class MavenArtifactTest {

    private static final Logger log = LoggerFactory.getLogger(MavenArtifactTest.class);
    
    // only *integration* tests require these to be *installed*;
    // note this may vary from machine to machine so version should be aligned with that in parent pom
    final static String MAVEN_JAR_PLUGIN_COORDINATE = "org.apache.maven.plugins:maven-jar-plugin:jar:2.6";
    final static String THIS_PROJECT_COORDINATE = "org.apache.brooklyn:brooklyn-utils-common:jar:0.9.0-SNAPSHOT";  // BROOKLYN_VERSION

    // Don't need to be installed, only used as examples
    final static String RELEASED_SOURCES_COORDINATE = "io.brooklyn:brooklyn-core:jar:sources:0.6.0";
    final static String EXAMPLE_BZIP_COORDINATE = "com.example:example-artifact:tar.bz2:server-windows:2.0.1";

    @Test
    public void testArtifact() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);
        
        Assert.assertEquals(m.getGroupId(), "org.apache.maven.plugins");
        Assert.assertEquals(m.getArtifactId(), "maven-jar-plugin");
        Assert.assertEquals(m.getVersion(), "2.6");
        Assert.assertEquals(m.getPackaging(), "jar");
        Assert.assertEquals(m.getClassifier(), null);
        
        Assert.assertEquals(m.getCoordinate(), MAVEN_JAR_PLUGIN_COORDINATE);
        
        Assert.assertEquals(m.getFilename(), "maven-jar-plugin-2.6.jar");
        Assert.assertEquals(m.isSnapshot(), false);
    }

    @Test
    public void testArtifactWithClassifier() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(RELEASED_SOURCES_COORDINATE);

        Assert.assertEquals(m.getGroupId(), "io.brooklyn");
        Assert.assertEquals(m.getArtifactId(), "brooklyn-core");
        Assert.assertEquals(m.getVersion(), "0.6.0");
        Assert.assertEquals(m.getPackaging(), "jar");
        Assert.assertEquals(m.getClassifier(), "sources");

        Assert.assertEquals(m.getCoordinate(), RELEASED_SOURCES_COORDINATE);

    }

    @Test
    public void testRetrieval() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);

        String hostedUrl = new MavenRetriever().getHostedUrl(m);
        Assert.assertTrue(hostedUrl.startsWith("http://search.maven.org/"));
        
        String localPath = new MavenRetriever().getLocalPath(m);
        Assert.assertTrue(localPath.endsWith(
                "/repository/org/apache/maven/plugins/maven-jar-plugin/2.6/maven-jar-plugin-2.6.jar"), 
                localPath);
    }

    @Test
    public void testRetrievalWithClassifier() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(RELEASED_SOURCES_COORDINATE);

        String localPath = new MavenRetriever().getLocalPath(m);
        Assert.assertTrue(localPath.endsWith(
                "/repository/io/brooklyn/brooklyn-core/0.6.0/brooklyn-core-0.6.0-sources.jar"),
                localPath);
    }

    @Test
    public void testRetrievalWithUnusualClassifier() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(EXAMPLE_BZIP_COORDINATE);

        String localPath = new MavenRetriever().getLocalPath(m);
        Assert.assertTrue(localPath.endsWith(
                "/repository/com/example/example-artifact/2.0.1/example-artifact-2.0.1-server-windows.tar.bz2"),
                localPath);
    }

    @Test
    public void testSnapshotRetrieval() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(THIS_PROJECT_COORDINATE);

        if (!m.isSnapshot()) {
            log.info("Skipping SNAPSHOT testing as this is not a snapshot project");
            return;
        }

        String hostedUrl = new MavenRetriever().getHostedUrl(m);
        Assert.assertTrue(hostedUrl.contains("repository.apache.org"), hostedUrl);
        
        String localPath = new MavenRetriever().getLocalPath(m);
        Assert.assertTrue(localPath.contains(
                "/repository/org/apache/brooklyn"));
    }

    /*
        Exception java.lang.AssertionError
        
        Message: Could not load /home/ubuntu/.m2/repository/org/apache/maven/plugins/maven-jar-plugin/2.6/maven-jar-plugin-2.6.jar when testing MavenRetriever: do a maven build with no integration tests first to ensure this is installed, then rerun
        Stacktrace:
        
        
        at org.testng.Assert.fail(Assert.java:94)
        at org.apache.brooklyn.util.maven.MavenArtifactTest.testRetrievalLocalIntegration(MavenArtifactTest.java:137)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
     */
    @Test(groups={"Integration","Broken"})
    public void testRetrievalLocalIntegration() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);

        String localPath = new MavenRetriever().getLocalPath(m);
        File f = new File(localPath);
        if (!f.exists())
            Assert.fail("Could not load "+localPath+" when testing MavenRetriever: do a maven build with no integration tests first to ensure this is installed, then rerun");
        
        checkValidMavenJarUrl(MavenRetriever.localUrl(m), "org/apache/maven/plugin/jar/JarMojo.class");
    }

    @Test(groups="Integration")
    public void testRetrievalHostedReleaseIntegration() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);

        checkValidMavenJarUrl(new MavenRetriever().getHostedUrl(m), "org/apache/maven/plugin/jar/JarMojo.class");
    }

    protected void checkAvailableUrl(String url) throws Exception {
        try {
            InputStream stream = new URL(url).openStream();
            stream.read();
            stream.close();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    protected void checkValidMavenJarUrl(String url, String resource) throws Exception {
        // URLClassLoader doesn't follow redirects; find out the real URL
        // Note URLClassLoader.close was only added in Java 7; do not call it until Java 6 support is not needed!
        URL realUrl = followRedirects(new URL(url));
        URLClassLoader classLoader = new URLClassLoader(new URL[] { realUrl });
        try {
            URL innerU = classLoader.findResource(resource);
            InputStream innerUin = innerU.openConnection().getInputStream();
            innerUin.close();
        } finally {
            classLoader.close();
        }
    }

    /*
        Exception java.lang.AssertionError
        
        Message: Could not load /home/ubuntu/.m2/repository/org/apache/brooklyn/brooklyn-utils-common/(version)/brooklyn-utils-common-(version)-SNAPSHOT.jar when testing MavenRetriever: do a maven build with no integration tests first to ensure this is installed, then rerun
        Stacktrace:
        
        
        at org.testng.Assert.fail(Assert.java:94)
        at org.apache.brooklyn.util.maven.MavenArtifactTest.testRetrievalHostedSnapshotIntegration(MavenArtifactTest.java:176)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
     */
    @Test(groups={"Integration","Broken"})
    public void testRetrievalHostedSnapshotIntegration() throws Exception {
        MavenArtifact m = MavenArtifact.fromCoordinate(
                "org.apache.brooklyn:brooklyn-utils-common:jar:0.9.0-SNAPSHOT");  // BROOKLYN_VERSION
        
        String localPath = new MavenRetriever().getLocalPath(m);
        File f = new File(localPath);
        if (!f.exists())
            Assert.fail("Could not load "+localPath+" when testing MavenRetriever: do a maven build with no integration tests first to ensure this is installed, then rerun");
        
        String l = new MavenRetriever().getLocalUrl(m);
        Assert.assertEquals(new URL(l), new URL("file://"+localPath));
        
        checkAvailableUrl(l);
        
        String h = new MavenRetriever().getHostedUrl(m);
        if (!m.isSnapshot()) {
            log.info("Skipping SNAPSHOT testing as this is not a snapshot build");
        } else {
            Assert.assertTrue(h.contains("repository.apache.org"), "h="+h);
        }

        try {
            checkAvailableUrl(h);
        } catch (Exception e) {
            // don't fail for now, just warn
            log.warn("Could not download SNAPSHOT build for "+h+": is it installed to sonatype?", e);
        }
    }

    private URL followRedirects(URL url) throws Exception {
        if ("file".equalsIgnoreCase(url.getProtocol())) return url;
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(5000);
     
        // normally, 3xx is redirect
        int status = conn.getResponseCode();
        boolean redirect = (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER);
     
        if (redirect) {
            // get redirect url from "location" header field
            String newUrl = conn.getHeaderField("Location");
            log.debug("Following redirect for "+url+", to "+newUrl);
            return followRedirects(new URL(newUrl));
        } else {
            return url;
        }
    }
}
