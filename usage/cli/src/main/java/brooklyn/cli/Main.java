/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.cli;

import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Option;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.rest.security.PasswordHasher;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.exceptions.UserFacingException;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects.ToStringHelper;

/**
 * This class is the primary CLI for brooklyn.
 * Run with the `help` argument for help.
 * <p>
 * This class is designed for subclassing, with subclasses typically:
 * <li> providing their own static {@link #main(String...)} (of course) which need simply invoke 
 *      {@link #execCli(String[])} with the arguments 
 * <li> returning their CLI name (e.g. "start.sh") in an overridden {@link #cliScriptName()}
 * <li> providing an overridden {@link LaunchCommand} via {@link #cliLaunchCommand()} if desired
 * <li> providing any other CLI customisations by overriding {@link #cliBuilder()}
 *      (typically calling the parent and then customizing the builder)
 * <li> populating a custom catalog using {@link LaunchCommand#populateCatalog(BrooklynCatalog)}
 */
public class Main extends AbstractMain {

    /** @deprecated since 0.7.0 will become private static, subclasses should define their own logger */
    @Deprecated
    public static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        new Main().execCli(args);
    }

    @Command(name = "generate-password", description = "Generates a hashed web-console password")
    public static class GeneratePasswordCommand extends BrooklynCommandCollectingArgs {

        @Option(name = { "--user" }, title = "username", required = true)
        public String user;

        @Option(name = { "--stdin" }, title = "read password from stdin, instead of console", 
                description = "Before using stdin, read http://stackoverflow.com/a/715681/1393883 for discussion of security!")
        public boolean useStdin;

        @Override
        public Void call() throws Exception {
            checkCanReadPassword();
            
            System.out.print("Enter password: ");
            System.out.flush();
            String password = readPassword();
            if (Strings.isBlank(password)) {
                throw new UserFacingException("Password must not be blank; aborting");
            }
            
            System.out.print("Re-enter password: ");
            System.out.flush();
            String password2 = readPassword();
            if (!password.equals(password2)) {
                throw new UserFacingException("Passwords did not match; aborting");
            }

            String salt = Identifiers.makeRandomId(4);
            String sha256password = PasswordHasher.sha256(salt, new String(password));
            
            System.out.println();
            System.out.println("Please add the following to your brooklyn.properties:");
            System.out.println();
            System.out.println("brooklyn.webconsole.security.users="+user);
            System.out.println("brooklyn.webconsole.security.user."+user+".salt="+salt);
            System.out.println("brooklyn.webconsole.security.user."+user+".sha256="+sha256password);

            return null;
        }
        
        private void checkCanReadPassword() {
            if (useStdin) {
                // yes; always
            } else {
                Console console = System.console();
                if (console == null) {
                    throw new FatalConfigurationRuntimeException("No console; cannot get password securely; aborting");
                }
            }
        }
        
        private String readPassword() throws IOException {
            if (useStdin) {
                return readLine(System.in);
            } else {
                return new String(System.console().readPassword());
            }
        }
        
        private String readLine(InputStream in) throws IOException {
            StringBuilder result = new StringBuilder();
            char c;
            while ((c = (char)in.read()) != '\n') {
                result.append(c);
            }
            return result.toString();
        }
    }
    
    

    @Command(name = "copy-state", description = "Retrieves persisted state")
    public static class CopyStateCommand extends BrooklynCommandCollectingArgs {

        @Option(name = { "--localBrooklynProperties" }, title = "local brooklyn.properties file",
                description = "local brooklyn.properties file, specific to this launch (appending to and overriding global properties)")
        public String localBrooklynProperties;

        @Option(name = { "--persistenceDir" }, title = "persistence dir",
                description = "The directory to read/write persisted state (or container name if using an object store)")
        public String persistenceDir;

        @Option(name = { "--persistenceLocation" }, title = "persistence location",
            description = "The location spec for an object store to read/write persisted state")
        public String persistenceLocation;
    
        @Option(name = { "--destinationDir" }, required = true, title = "destination dir",
                description = "The directory to copy persistence data to")
            public String destinationDir;
        
        @Override
        public Void call() throws Exception {
            File destinationDirF = new File(Os.tidyPath(destinationDir));
            if (destinationDirF.isFile()) throw new FatalConfigurationRuntimeException("Destination directory is a file: "+destinationDir);


            // Configure launcher
            BrooklynLauncher launcher;
            failIfArguments();
            try {
                log.info("Retrieving and copying persisted state to "+destinationDirF.getAbsolutePath());
                
                if (!quiet) stdout.println(BANNER);
    
                PersistMode persistMode = PersistMode.AUTO;
                HighAvailabilityMode highAvailabilityMode = HighAvailabilityMode.DISABLED;
                
                launcher = BrooklynLauncher.newInstance()
                        .localBrooklynPropertiesFile(localBrooklynProperties)
                        .persistMode(persistMode)
                        .persistenceDir(persistenceDir)
                        .persistenceLocation(persistenceLocation)
                        .highAvailabilityMode(highAvailabilityMode);
                
            } catch (FatalConfigurationRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new FatalConfigurationRuntimeException("Fatal error configuring Brooklyn launch: "+e.getMessage(), e);
            }
            
            try {
                BrooklynMemento memento = launcher.retrieveState();
                launcher.persistState(memento, destinationDirF);
                
            } catch (FatalRuntimeException e) {
                // rely on caller logging this propagated exception
                throw e;
            } catch (Exception e) {
                // for other exceptions we log it, possibly redundantly but better too much than too little
                Exceptions.propagateIfFatal(e);
                log.error("Error retrieving persisted state: "+Exceptions.collapseText(e), e);
                Exceptions.propagate(e);
            } finally {
                try {
                    launcher.terminate();
                } catch (Exception e2) {
                    log.warn("Subsequent error during termination: "+e2);
                    log.debug("Details of subsequent error during termination: "+e2, e2);
                }
            }
            
            return null;
        }

        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("localBrooklynProperties", localBrooklynProperties)
                    .add("persistenceLocation", persistenceLocation)
                    .add("persistenceDir", persistenceDir)
                    .add("destinationDir", destinationDir);
        }
    }

    /** method intended for overriding when the script filename is different 
     * @return the name of the script the user has invoked */
    protected String cliScriptName() {
        return "brooklyn";
    }

    /** method intended for overriding when a different {@link Cli} is desired,
     * or when the subclass wishes to change any of the arguments */
    @Override
    protected CliBuilder<BrooklynCommand> cliBuilder() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.<BrooklynCommand>builder(cliScriptName())
                .withDescription("Brooklyn Management Service")
                .withCommands(
                        HelpCommand.class,
                        InfoCommand.class,
                        GeneratePasswordCommand.class,
                        CopyStateCommand.class,
                        cliLaunchCommand()
                );

        return builder;
    }
    
}
