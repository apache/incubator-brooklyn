package brooklyn.rest.commands;

import brooklyn.rest.api.LocationSpec;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.io.File;
import java.net.URI;
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
  protected void run(Json json, JerseyClient client, CommandLine params) {
    URI endpoint = URI.create(params.getOptionValue("endpoint", "http://localhost:8080") + "/v1/locations");

    try {
      if (params.getArgList().size() < 1)
        throw new RuntimeException("Path to JSON file is mandatory.");

      String jsonFileName = (String) params.getArgList().get(0);
      LocationSpec spec = json.readValue(new File(jsonFileName), LocationSpec.class);

      ClientResponse response = client.post(endpoint, MediaType.APPLICATION_JSON_TYPE,
          spec, ClientResponse.class);

      System.out.println("Ok: " + response.getLocation());

    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }
}
