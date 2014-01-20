package brooklyn.entity.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.launcher.camp.SimpleYamlLauncher;
import brooklyn.util.ResourceUtils;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

import com.google.common.collect.Iterables;

public class VanillaSoftwareYamlTest {

    private static final Logger log = LoggerFactory.getLogger(VanillaSoftwareYamlTest.class);
    
    /** yaml variant of VanillaSoftwareProcessAndChildrenIntegrationTest */
    @Test(groups="Integration")
    public void testVanillaSoftwareYaml() {
        SimpleYamlLauncher l = new SimpleYamlLauncher();
        try {
            Application app = l.launchAppYaml("vanilla-software-with-child-blueprint.yaml");
            log.info("started "+app);

            Entity p1 = Iterables.getOnlyElement( app.getChildren() );
            Long d1 = Long.parseLong( Strings.getFirstWordAfter(new ResourceUtils(this).getResourceAsString(Os.mergePaths(p1.getAttribute(SoftwareProcess.RUN_DIR), "DATE")), "utc") );
            
//            // TODO add support for adding children via yaml! - see the yaml file above also
//            Entity p2 = Iterables.getOnlyElement( p1.getChildren() );
//            Long d2 = Long.parseLong( Strings.getFirstWordAfter(new ResourceUtils(this).getResourceAsString(Os.mergePaths(p2.getAttribute(SoftwareProcess.RUN_DIR), "DATE")), "utc") );
//            Assert.assertTrue( d2-d1 > 2 && d2-d1 < 10, "p2 should have started 3s after parent, but it did not ("+(d2-d1)+"s difference" );
        } finally {
            l.destroyAll();
        }
        log.info("DONE");
    }
    
}
