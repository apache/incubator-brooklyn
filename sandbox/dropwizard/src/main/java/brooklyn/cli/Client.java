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
import io.airlift.command.Cli;
import io.airlift.command.Help;
import io.airlift.command.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;

public class Client {

    public static final Logger LOG = LoggerFactory.getLogger(Client.class);

    // Error codes
    public static final int PARSE_ERROR = 1;
    public static final int EXECUTION_ERROR = 2;

    private PrintStream out;
    private PrintStream err;

    private static Cli<BrooklynCommand> parser = buildCli();

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
        try {
            client.run(args);
        } catch (ParseException pe) {
            // looks like the user typed it wrong
            LOG.error("Error parsing command "+ Arrays.toString(args), pe);
            System.err.println("Parse error: " + pe.getMessage());
            System.err.println(getUsageInfo(parser));
            System.exit(PARSE_ERROR);
        } catch (Exception e) {
            // unexpected error during command execution
            LOG.error("Error executing command "+ Arrays.toString(args), e);
            System.err.println("Execution error: " + e.getMessage());
            System.exit(EXECUTION_ERROR);
        }
    }

    public final void run(String...args) throws Exception {
        LOG.debug("Parsing command line arguments: {}", args);
        BrooklynCommand command = parser.parse(args);
        command.setOut(out);
        command.setErr(err);
        LOG.debug("Executing command: {}", command);
        command.call();
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
