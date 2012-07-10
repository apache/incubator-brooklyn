package brooklyn.cli.commands;

import brooklyn.cli.Client;
import org.iq80.cli.Command;
import org.iq80.cli.Help;

import javax.inject.Inject;

@Command(name = "help", description = "Display help information about brooklyn")
public class HelpCommand extends BrooklynCommand {

    @Inject
    Help help;

    @Override
    public Void call() throws Exception {

        // Common command behavior
        super.call();

        return help.call();
    }

}


