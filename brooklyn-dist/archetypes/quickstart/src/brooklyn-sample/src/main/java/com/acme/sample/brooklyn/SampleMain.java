package com.acme.sample.brooklyn;

import java.util.Arrays;

import io.airlift.command.Command;
import io.airlift.command.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.cli.Main;

import com.google.common.base.Objects.ToStringHelper;

import com.acme.sample.brooklyn.sample.app.*;

/**
 * This class provides a static main entry point for launching a custom Brooklyn-based app.
 * <p>
 * It inherits the standard Brooklyn CLI options from {@link Main},
 * plus adds a few more shortcuts for favourite blueprints to the {@link LaunchCommand}.
 */
public class SampleMain extends Main {
    
    private static final Logger log = LoggerFactory.getLogger(SampleMain.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static void main(String... args) {
        log.debug("CLI invoked with args "+Arrays.asList(args));
        new SampleMain().execCli(args);
    }

    @Override
    protected String cliScriptName() {
        return "start.sh";
    }
    
    @Override
    protected Class<? extends BrooklynCommand> cliLaunchCommand() {
        return LaunchCommand.class;
    }

    @Command(name = "launch", description = "Starts a server, and optionally an application. "
        + "Use e.g. --single or --cluster to launch one-node and clustered variants of the sample web application.")
    public static class LaunchCommand extends Main.LaunchCommand {

        // add these options to the LaunchCommand as shortcuts for our favourite applications
        
        @Option(name = { "--single" }, description = "Launch a single web-server instance")
        public boolean single;

        @Option(name = { "--cluster" }, description = "Launch a web-server cluster")
        public boolean cluster;

        @Override
        public Void call() throws Exception {
            // process our CLI arguments
            if (single) setAppToLaunch( SingleWebServerSample.class.getCanonicalName() );
            if (cluster) setAppToLaunch( ClusterWebServerDatabaseSample.class.getCanonicalName() );
            
            // now process the standard launch arguments
            return super.call();
        }

        @Override
        protected void populateCatalog(BrooklynCatalog catalog) {
            super.populateCatalog(catalog);
            catalog.addItem(SingleWebServerSample.class);
            catalog.addItem(ClusterWebServerDatabaseSample.class);
        }

        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("single", single)
                    .add("cluster", cluster);
        }
    }
}
