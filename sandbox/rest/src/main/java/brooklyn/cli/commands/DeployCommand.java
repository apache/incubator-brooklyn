package brooklyn.cli.commands;

import brooklyn.cli.Client;
import org.iq80.cli.Command;
import org.iq80.cli.Option;
import org.iq80.cli.Arguments;

@Command(name = "deploy", description = "Deploys the specified application using given config, classpath, location, etc")
public class DeployCommand extends BrooklynCommand {

    @Option(name = "--format",
            description = "Either json,groovy,class, to force APP type detection")
    public String format;

    @Option(name = "--no-start",
            description = "Don't invoke `start` on the application")
    public boolean noStart = false;

    @Option(name = { "--location", "--locations" },
            title = "Location list",
            description = "Specifies the locations where the application will be launched. You can specify more than one location like this: \"loc1,loc2,loc3\"")
    public String locations;

    @Option(name = "--config",
            title = "Configuration parameters list",
            description = "Pass the config parameters to the application like this: \"A=B,C=D\"")
    public String config;

    @Option(name = "--classpath",
            description = "Upload the given classes")
    public String classpath;

    @Arguments(title = "APP",
            description = "where APP can be\n" +
                    "    * a fully qualified class-name of something on the classpath\n" +
                    "    * path or URL to a script file (if ends .groovy)\n" +
                    "    * path or URL to a JSON file (if ends .json)")
    public String app;

    @Override
    public Void call() throws Exception {
        System.out.println("Invoked deploy command stub . . .");
        return null;
    }

}


