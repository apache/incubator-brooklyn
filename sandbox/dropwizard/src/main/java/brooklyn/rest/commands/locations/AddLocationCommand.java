package brooklyn.rest.commands.locations;

import brooklyn.rest.commands.BrooklynCommand;
import brooklyn.rest.domain.LocationSpec;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.PrintStream;

import static com.google.common.base.Preconditions.checkArgument;

public class AddLocationCommand extends BrooklynCommand {

  public AddLocationCommand() {
    super("add-location", "Add a new location from JSON spec file.");
  }

  @Override
  public String getSyntax() {
    return "[options] <json file>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Path to JSON file is mandatory");

    String jsonFileName = (String) params.getArgList().get(0);
    LocationSpec spec = json.readValue(new File(jsonFileName), LocationSpec.class);

    ClientResponse response = client.resource(uriFor("/v1/locations"))
        .type(MediaType.APPLICATION_JSON_TYPE).post(ClientResponse.class, spec);

    out.println("Ok: " + response.getLocation());
  }
}
