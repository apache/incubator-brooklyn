package brooklyn.rest.commands.applications;

import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import java.io.PrintStream;

import static com.google.common.base.Preconditions.checkArgument;

public class DeleteApplicationCommand extends BrooklynCommand {

  public DeleteApplicationCommand() {
    super("delete-application", "Delete application by name");
  }

  @Override
  public String getSyntax() {
    return "[options] <application name>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Application name is mandatory");

    String name = (String) params.getArgList().get(0);
    ClientResponse response = client.resource(uriFor("/v1/applications/" + name))
      .delete(ClientResponse.class);

    out.println("Ok, status: " + response.getStatus());
  }
}
