package brooklyn.cli;

import brooklyn.cli.commands.BrooklynCommand;
import brooklyn.cli.commands.DeployCommand;
import brooklyn.cli.commands.HelpCommand;
import brooklyn.cli.commands.VersionCommand;
import brooklyn.cli.commands.UndeployCommand;
import brooklyn.cli.commands.CatalogEntitiesCommand;
import brooklyn.cli.commands.CatalogPoliciesCommand;
import brooklyn.cli.commands.ListApplicationsCommand;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.iq80.cli.Cli;
import org.iq80.cli.Help;
import org.iq80.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class Client {

    // Error codes
    public static final int PARSE_ERROR = 1;
    public static final int EXECUTION_ERROR = 2;

    public static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private PrintStream out;
    private PrintStream err;

    public Client() {
        this.out = System.out;
        this.err = System.err;
    }

    public Client(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String...args) {
        Client client = new Client();
        client.run(args);
    }

    public final void run(String...args) {
        Cli<BrooklynCommand> parser = buildCli();
        try {
            LOG.debug("Parsing command line arguments: {}", args);
            BrooklynCommand command = parser.parse(args);
            command.setOut(out);
            command.setErr(err);
            LOG.debug("Executing command: {}", command);
            command.call();
        } catch (ParseException pe) { // looks like the user typed it wrong
            System.err.println("Parse error: " + pe.getMessage()); // display error
            System.err.println(getUsageInfo(parser)); // display cli help
            System.exit(PARSE_ERROR);
        } catch (Exception e) { // unexpected error during command execution
            LOG.error("Execution error: {}\n{}" + e.getMessage(), e.getStackTrace());
            System.err.println("Execution error: " + e.getMessage());
            e.printStackTrace();
            System.exit(EXECUTION_ERROR);
        }
    }

    @VisibleForTesting
    static Cli<BrooklynCommand> buildCli() {
        @SuppressWarnings({ "unchecked" })
        Cli.CliBuilder<BrooklynCommand> builder = Cli.buildCli("brooklyn", BrooklynCommand.class)
                .withDescription("Brooklyn CLI client")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(
                        HelpCommand.class,
                        VersionCommand.class,
                        DeployCommand.class,
                        UndeployCommand.class,
                        ListApplicationsCommand.class,
                        CatalogEntitiesCommand.class,
                        CatalogPoliciesCommand.class
                );
        return builder.build();
    }

    static String getUsageInfo(Cli<BrooklynCommand> parser) {
        StringBuilder help = new StringBuilder();
        help.append("\n");
        Help.help(parser.getMetadata(), ImmutableList.of("brooklyn"),help);
        help.append("See 'brooklyn help <command>' for more information on a specific command.");
        return help.toString();
    }

}
