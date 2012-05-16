package brooklyn.cli;

import static org.testng.Assert.assertTrue;
import groovy.lang.GroovyClassLoader;

import org.testng.annotations.Test;

import brooklyn.cli.Main.Launch;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.util.ResourceUtils;

public class CliTest {

    @Test
    public void testClassloadsApplication() throws Exception {
        Launch launchCommand = new Main.Launch();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        String appName = ExampleApp.class.getName();
        
        AbstractApplication app = launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
        assertTrue(app instanceof ExampleApp, "app="+app);
    }
    
    @SuppressWarnings("serial")
    public static class ExampleApp extends AbstractApplication {}
    
}
