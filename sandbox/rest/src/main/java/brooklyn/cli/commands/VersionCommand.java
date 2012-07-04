package brooklyn.cli.commands;

import brooklyn.cli.Client;
import org.iq80.cli.Command;

@Command(name = "version", description = "Print version")
public class VersionCommand extends BrooklynCommand {

    @Override
    public Void call() throws Exception {
        System.out.println("Invoked version command stub . . .");
        return null;
    }

}


