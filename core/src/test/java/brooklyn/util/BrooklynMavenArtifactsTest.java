package brooklyn.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.maven.MavenArtifact;
import brooklyn.util.maven.MavenRetriever;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

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
        Time.sleep(Duration.FIVE_SECONDS);
        log.info("boo!");
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
        String snapshot = MavenRetriever.hostedUrl(MavenArtifact.fromCoordinate("io.brooklyn:brooklyn-utils-common:jar:0.7.0-SNAPSHOT"));
        log.info("Sample snapshot URL is: "+snapshot);
        checkValidArchive(snapshot);
        ResourceUtils.create(this).checkUrlExists(snapshot);
        
        // NB: this should be a version known to be up at sonatype or maven central, NOT necessarily the current version!
        String release = MavenRetriever.hostedUrl(MavenArtifact.fromCoordinate("io.brooklyn:brooklyn-utils-common:jar:0.6.0"));
        log.info("Sample release URL is: "+release);
        checkValidArchive(release);
    }

    private void checkValidArchive(String url) {
        try {
            byte[] bytes = Streams.readFully(ResourceUtils.create(this).getResourceFromUrl(url));
            // confirm this follow redirects!
            Assert.assertTrue(bytes.length > 100*1000, "download of "+url+" is suspect ("+Strings.makeSizeString(bytes.length)+")");
            // (could also check it is a zip etc)
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

}
