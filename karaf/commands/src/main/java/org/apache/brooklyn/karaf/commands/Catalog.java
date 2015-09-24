
package org.apache.brooklyn.karaf.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Command(scope = "brooklyn", name = "catalog", description = "Manage the local brooklyn catalog")
@Service
public class Catalog implements Action {

    @Option(name = "-o", aliases = { "--option" }, description = "An option to the command", required = false, multiValued = false)
    private String option;

    @Argument(name = "argument", description = "Argument to the command", required = false, multiValued = false)
    private String argument;

    @Override
    public Object execute() throws Exception {
         System.out.println("Executing command catalog");
         System.out.println("Option: " + option);
         System.out.println("Argument: " + argument);
         return null;
    }
}
