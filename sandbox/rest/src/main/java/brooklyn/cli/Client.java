package brooklyn.cli;

import brooklyn.cli.commands.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.iq80.cli.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {

    // Error codes
    public static final int PARSE_ERROR = 1;
    public static final int EXECUTION_ERROR = 2;

    public static final Logger log = LoggerFactory.getLogger(Client.class);

    public static void main(String...args) {
        Cli<BrooklynCommand> parser = buildCli();
        try {
            log.debug("Parsing command line arguments: {}",args);
            BrooklynCommand command = parser.parse(args);
            log.debug("Executing command: {}", command);
            command.call();
        } catch (ParseException pe) { // looks like the user typed it wrong
            System.err.println("Parse error: " + pe.getMessage()); // display error
            System.err.println(getUsageInfo(parser)); // display cli help
            System.exit(PARSE_ERROR);
        } catch (Exception e) { // unexpected error during command execution
            log.error("Execution error: {}\n{}" + e.getMessage(),e.getStackTrace());
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
                        ListEntitiesCommand.class,
                        ListPoliciesCommand.class
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
