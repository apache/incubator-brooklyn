package brooklyn.cli.commands;

import brooklyn.cli.Client;
import io.airlift.command.Command;
import io.airlift.command.Help;

import javax.inject.Inject;

@Command(name = "help", description = "Display help information about brooklyn")
public class HelpCommand extends BrooklynCommand {

    @Inject
    Help help;

    @Override
    public void run() throws Exception {
        help.call();
    }

}


