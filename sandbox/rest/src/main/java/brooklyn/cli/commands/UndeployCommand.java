package brooklyn.cli.commands;

import org.iq80.cli.Arguments;
import org.iq80.cli.Command;
import org.iq80.cli.Option;

@Command(name = "undeploy", description = "Undeploys the specified application")
public class UndeployCommand extends BrooklynCommand {

    @Option(name = "--no-stop",
            description = "Don't invoke `stop` on the application")
    public boolean noStart = false;

    @Arguments(title = "APP",
            description = "where APP can be\n" +
                    "    * a fully qualified class-name of something on the classpath\n" +
                    "    * path or URL to a script file (if ends .groovy)\n" +
                    "    * path or URL to a JSON file (if ends .json)")
    public String app;

    @Override
    public Void call() throws Exception {
        System.out.println("Invoked undeploy command stub . . .");
        return null;
    }

}


