package ${package};

import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.cli.Main;
import brooklyn.launcher.FatalConfigurationRuntimeException;

import com.google.common.base.Objects.ToStringHelper;
import ${package}.sample.app.ClusterWebServerDatabaseSample;
import ${package}.sample.app.SingleWebServerSample;

public class SampleMain extends Main {
    
    private static final Logger log = LoggerFactory.getLogger(SampleMain.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static void main(String... args) {
        Cli<BrooklynCommand> parser = buildCli();
        Main.execCli(parser, args);
    }

    static Cli<BrooklynCommand> buildCli() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.buildCli("start.sh", BrooklynCommand.class)
                .withDescription("Brooklyn Management Service")
                .withCommands(
                        HelpCommand.class,
                        InfoCommand.class,
                        LaunchCommand.class
                );

        return builder.build();
    }

    @Command(name = "launch", description = "Starts a brooklyn application. " +
            "Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand " +
            "to point to the user application classpath.")
    public static class LaunchCommand extends Main.LaunchCommand {

        @Option(name = { "--single" }, title = "launch single web-server instance")
        public boolean single;

        @Option(name = { "--cluster" }, title = "launch web-server cluster")
        public boolean cluster;

        @Override
        public Void call() throws Exception {
            if (app != null) {
                if (single || cluster) {
                    throw new FatalConfigurationRuntimeException("Cannot specify app and either of single or cluster");
                }
            } else if (single) {
                if (cluster) {
                    throw new FatalConfigurationRuntimeException("Cannot specify single and cluster");
                }
                app = SingleWebServerSample.class.getCanonicalName();
            } else if (cluster) {
                app = ClusterWebServerDatabaseSample.class.getCanonicalName();
            }
            
            return super.call();
        }

        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("single", single)
                    .add("cluster", cluster);
        }
    }
}
