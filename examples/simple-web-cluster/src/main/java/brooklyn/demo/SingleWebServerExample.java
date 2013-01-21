package brooklyn.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/** This example starts one web app on 8080, waits for a keypress, then stops it. */
public class SingleWebServerExample extends ApplicationBuilder {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerExample.class);

    public static final String DEFAULT_LOCATION = "localhost";
    
    private static final String WAR_PATH = "classpath://hello-world-webapp.war";

    @Override
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(JBoss7Server.class)
                .configure("war", WAR_PATH)
                .configure("httpPort", 8080));
    }

    // Shows how to use ApplicationBuilder without sub-classing, but for CLI usage one should sub-class
    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        // TODO Want to parse, to handle multiple locations
        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        BasicApplication app = ApplicationBuilder.builder()
                .child(BasicEntitySpec.newInstance(JBoss7Server.class)
                        .configure("war", WAR_PATH)
                        .configure("httpPort", 8080))
                .manage();
        JBoss7Server web = (JBoss7Server) Iterables.getOnlyElement(app.getChildren());

        LOG.info("created, now starting...");
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
        LOG.info("started, visit "+web.getAttribute(JBoss7Server.ROOT_URL));
    }
}
