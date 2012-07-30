package brooklyn.launcher;

import brooklyn.config.BrooklynServiceAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    public static final Logger log = LoggerFactory.getLogger(Main.class);

    private WebAppRunner launcher = newDefaultWebAppRunner();

    public static void main(String... args) throws Exception {
        new Main().run(args);
    }

    public WebAppRunner newDefaultWebAppRunner() {
        return new WebAppRunner(null);
    }

    public void run(String[] args) throws Exception {
        configureFromArgs(args);
        startWebApp();
    }

    public WebAppRunner startWebApp() throws Exception {
        launcher.start();
        return launcher;
    }

    //TODO allow specifying default war and add'l wars by URL (--war, --wars)
    //TODO allow specifying a script file/url to run (-f /tmp/MyApp.groovy)
    //TODO allow specifying a script on the cli (-e '{ new MyApp() }')
    //TODO command-line options in a way that generates help, and is easily overridden,
    //     and supports -h --help to print the options
    public void configureFromArgs(String[] args) throws Exception {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--httpPort".equals(args[i])) {
                launcher.setPort(Integer.parseInt(args[++i]));
            } else if ("--autologinAdmin".equals(args[i])) {
                launcher.addAttribute(BrooklynServiceAttributes.BROOKLYN_AUTOLOGIN_USERNAME, "admin");
            } else {
                log.warn("Unsupported command-line argument: " + args[i]);
            }
        }
    }

}
