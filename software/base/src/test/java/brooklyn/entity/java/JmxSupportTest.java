package brooklyn.entity.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.UsesJmx.JmxAgentModes;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.maven.MavenRetriever;
import brooklyn.util.text.Strings;

@Test
public class JmxSupportTest {

    private static final Logger log = LoggerFactory.getLogger(JmxSupportTest.class);
    
    private TestApplication app;

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
    }
    
    // defaults to JMXMP for most locations (or, in this case, if it does not yet know the location)
    public void testJmxrmiAutodetect() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        JmxSupport support = new JmxSupport(app, null);
        
        Assert.assertEquals(support.getJmxAgentMode(), JmxAgentModes.JMXMP);
    }

    public void testJmxmpJarExistence() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        app.setConfig(JmxSupport.JMX_AGENT_MODE, JmxAgentModes.JMXMP);
        JmxSupport support = new JmxSupport(app, null);
        
        Assert.assertEquals(support.getJmxAgentJarMavenArtifact().getArtifactId(),
                "brooklyn-jmxmp-agent");
        
        Assert.assertTrue(new ResourceUtils(this).doesUrlExist(support.getJmxAgentJarUrl()), support.getJmxAgentJarUrl());
        Assert.assertTrue(support.getJmxAgentJarUrl().contains("-shaded-"), support.getJmxAgentJarUrl());
    }
    
    public void testJmxrmiJarExistence() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        JmxSupport support = new JmxSupport(app, null);
        app.setConfig(JmxSupport.JMX_AGENT_MODE, JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
        
        Assert.assertEquals(support.getJmxAgentJarMavenArtifact().getArtifactId(),
                "brooklyn-jmxrmi-agent");
        
        Assert.assertTrue(new ResourceUtils(this).doesUrlExist(support.getJmxAgentJarUrl()), support.getJmxAgentJarUrl());
    }

    @Test(groups="Integration")
    public void testJmxmpJarHostedValidity() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        app.setConfig(JmxSupport.JMX_AGENT_MODE, JmxAgentModes.JMXMP);
        JmxSupport support = new JmxSupport(app, null);

        // make sure we get a valid jar, big enough (no redirect, and classifier correclty set for this!)
        // (we don't want the unshaded jar, that would be no good!)
        checkValidArchive(MavenRetriever.hostedUrl(support.getJmxAgentJarMavenArtifact()), 100*1000);
    }
    
    @Test(groups="Integration")
    public void testJmxrmiJarHostedValidity() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        JmxSupport support = new JmxSupport(app, null);
        app.setConfig(JmxSupport.JMX_AGENT_MODE, JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
        
        // make sure we get a valid jar, big enough (no redirect)
        checkValidArchive(MavenRetriever.hostedUrl(support.getJmxAgentJarMavenArtifact()), 4000);
    }

    private void checkValidArchive(String url, long minSize) {
        byte[] bytes;
        try {
            bytes = ResourceUtils.readFullyBytes(new ResourceUtils(this).getResourceFromUrl(url));
            log.info("read "+bytes.length+" bytes from "+url+" for "+JavaClassNames.callerNiceClassAndMethod(1));
        } catch (Exception e) {
            log.warn("Unable to read URL "+url+" for " +JavaClassNames.callerNiceClassAndMethod(1)+
            		"; this test may require hosted (sonatype/mavencentral) repo to be populated");
            Assert.fail("Unable to read URL "+url+"; this test may require hosted (sonatype/mavencentral) repo to be populated");
            throw Exceptions.propagate(e);
        }
        // confirm this follow redirects!
        Assert.assertTrue(bytes.length > minSize, "download of "+url+" is suspect ("+Strings.makeSizeString(bytes.length)+")");
        // (could also check it is a zip etc)
    }

}
