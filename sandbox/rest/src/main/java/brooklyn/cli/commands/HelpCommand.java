package brooklyn.cli.commands;

import brooklyn.cli.Client;
import org.iq80.cli.Command;

@Command(name = "help", description = "Display help information about brooklyn")
public class HelpCommand extends BrooklynCommand {
    @Override
    public Void call() throws Exception {
        Client.log.debug("Invoked help command");
        return help.call();
    }
}


