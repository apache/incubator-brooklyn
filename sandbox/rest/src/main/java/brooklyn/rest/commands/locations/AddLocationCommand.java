package brooklyn.rest.commands.locations;

import brooklyn.rest.api.LocationSpec;
import brooklyn.rest.commands.BrooklynCommand;
import static com.google.common.base.Preconditions.checkArgument;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.io.File;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class AddLocationCommand extends BrooklynCommand {

  public AddLocationCommand() {
    super("add-location", "Add a new location from JSON spec file.");
  }

  @Override
  public String getSyntax() {
    return "[options] <json file>";
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Path to JSON file is mandatory");

    String jsonFileName = (String) params.getArgList().get(0);
    LocationSpec spec = json.readValue(new File(jsonFileName), LocationSpec.class);

    ClientResponse response = client.post(uriFor("/v1/locations"),
        MediaType.APPLICATION_JSON_TYPE, spec, ClientResponse.class);

    System.out.println("Ok: " + response.getLocation());
  }
}
