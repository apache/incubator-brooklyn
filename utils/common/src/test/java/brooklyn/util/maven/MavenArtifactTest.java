package brooklyn.util.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;

@Test
public class MavenArtifactTest {

    private static final Logger log = LoggerFactory.getLogger(MavenArtifactTest.class);
    
    // only *integration* tests require these to be *installed*;
    // note this may vary from machine to machine so version should be aligned with that in parent pom
    final static String MAVEN_JAR_PLUGIN_COORDINATE = "org.apache.maven.plugins:maven-jar-plugin:jar:2.4";
    final static String THIS_PROJECT_COORDINATE = "io.brooklyn:brooklyn-utils-common:jar:0.6.0-SNAPSHOT";  // BROOKLYN_VERSION
    
    public void testArtifact() {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);
        
        Assert.assertEquals(m.getGroupId(), "org.apache.maven.plugins");
        Assert.assertEquals(m.getArtifactId(), "maven-jar-plugin");
        Assert.assertEquals(m.getVersion(), "2.4");
        Assert.assertEquals(m.getPackaging(), "jar");
        Assert.assertEquals(m.getClassifier(), null);
        
        Assert.assertEquals(m.getCoordinate(), MAVEN_JAR_PLUGIN_COORDINATE);
        
        Assert.assertEquals(m.getFilename(), "maven-jar-plugin-2.4.jar");
        Assert.assertEquals(m.isSnapshot(), false);
    }

    public void testRetrieval() {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);

        String hostedUrl = new MavenRetriever().getHostedUrl(m);
        Assert.assertTrue(hostedUrl.startsWith("http://search.maven.org/"));
        
        String localPath = new MavenRetriever().getLocalPath(m);
        Assert.assertTrue(localPath.endsWith(
                "/repository/org/apache/maven/plugins/maven-jar-plugin/2.4/maven-jar-plugin-2.4.jar"), 
                localPath);
    }

    public void testSnapshotRetrieval() {
        MavenArtifact m = MavenArtifact.fromCoordinate(THIS_PROJECT_COORDINATE);

        if (!m.isSnapshot()) {
            log.info("Skipping SNAPSHOT testing as this is not a snapshot project");
            return;
        }

        String hostedUrl = new MavenRetriever().getHostedUrl(m);
        Assert.assertTrue(hostedUrl.contains("sonatype"), hostedUrl);
        
        String localPath = new MavenRetriever().getLocalPath(m);
        Assert.assertTrue(localPath.contains(
                "/repository/io/brooklyn"));
    }

    @Test(groups="Integration")
    public void testRetrievalLocalIntegration() throws IOException {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);

        String localPath = new MavenRetriever().getLocalPath(m);
        File f = new File(localPath);
        if (!f.exists())
            Assert.fail("Could not load "+localPath+" when testing MavenRetriever: do a maven build with no integration tests first to ensure this is installed, then rerun");
        
        checkValidMavenJarUrl(MavenRetriever.localUrl(m));
    }

    @Test(groups="Integration")
    public void testRetrievalHostedReleaseIntegration() {
        MavenArtifact m = MavenArtifact.fromCoordinate(MAVEN_JAR_PLUGIN_COORDINATE);

        checkValidMavenJarUrl(new MavenRetriever().getHostedUrl(m));
    }

    protected void checkAvailableUrl(String url) {
        try {
            InputStream stream = new URL(url).openStream();
            stream.read();
            stream.close();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    protected void checkValidMavenJarUrl(String url) {
        try {
            URL innerU = new URLClassLoader(new URL[] { new URL(url) }).findResource(
                    "org/apache/maven/plugin/jar/JarMojo.class");
            InputStream innerUin = innerU.openConnection().getInputStream();
            innerUin.close();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Test(groups="Integration")
    public void testRetrievalHostedSnapshotIntegration() {
        MavenArtifact m = MavenArtifact.fromCoordinate(
                "io.brooklyn:brooklyn-utils-common:jar:0.6.0-SNAPSHOT");  // BROOKLYN_VERSION
        
        String localPath = new MavenRetriever().getLocalPath(m);
        File f = new File(localPath);
        if (!f.exists())
            Assert.fail("Could not load "+localPath+" when testing MavenRetriever: do a maven build with no integration tests first to ensure this is installed, then rerun");
        
        String l = new MavenRetriever().getLocalUrl(m);
        Assert.assertEquals(l, "file://"+localPath);
        
        checkAvailableUrl(l);
        
        String h = new MavenRetriever().getHostedUrl(m);
        if (!m.isSnapshot()) {
            log.info("Skipping SNAPSHOT testing as this is not a snapshot build");
        } else {
            Assert.assertTrue(h.contains("sonatype.org"));
        }

        try {
            checkAvailableUrl(h);
        } catch (Exception e) {
            // don't fail for now, just warn
            log.warn("Could not download SNAPSHOT build for "+h+": is it installed to sonatype?", e);
        }
    }


}
