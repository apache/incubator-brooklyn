package brooklyn.launcher;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.Maps;

public class Main {
    public static final Logger log = LoggerFactory.getLogger(Main.class);

    WebAppRunner launcher = newDefaultWebAppRunner();
    
    public static void main(String ...args) throws Exception {
        new Main().run(args);
    }
    
    public WebAppRunner newDefaultWebAppRunner() {
        new WebAppRunner(null);
    }
    
    public void run(String[] args) throws Exception {
        configureFromArgs(args);
        startWebApp();
    }

    public WebAppRunner startWebApp() throws Exception {
        launcher.start();
        launcher        
    }
    
    //TODO allow specifying default war and add'l wars by URL (--war, --wars)
    //TODO allow specifying a script file/url to run (-f /tmp/MyApp.groovy)
    //TODO allow specifying a script on the cli (-e '{ new MyApp() }')
    //TODO command-line options in a way that generates help, and is easily overridden,
    //     and supports -h --help to print the options
    public void configureFromArgs(String[] args) throws Exception {
        for (int i=0; i<args.length-1; i++) {
            if ("--httpPort".equals(args[i])) {
                launcher.port = Integer.parseInt(args[++i]);
            } else if ("--autologinAdmin".equals(args[i])) {
                launcher.attributes[BrooklynServiceAttributes.BROOKLYN_AUTOLOGIN_USERNAME] = 'admin'
            } else {
                log.warn("Unsupported command-line argument: "+args[i]);
            }
        }
    }
    
}
