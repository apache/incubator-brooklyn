package brooklyn.rest.commands.applications;


import brooklyn.rest.commands.BrooklynCommand;
import com.google.common.collect.ImmutableMap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintStream;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class InvokeEffectorCommand extends BrooklynCommand {

  public InvokeEffectorCommand() {
    super("invoke-effector", "Invoke entity effector (no arguments)");
  }

  @Override
  public String getSyntax() {
    return "[options] <effector uri>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Effector URI is mandatory");

    URI effectorUri = uriFor((String) params.getArgList().get(0));
    ClientResponse response = client.resource(effectorUri)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(ImmutableMap.<String, String>of())
      .post(ClientResponse.class);

    checkState(response.getStatus() == Response.Status.ACCEPTED.getStatusCode(),
      "Got unexpected response: " + response.toString());
  }
}
