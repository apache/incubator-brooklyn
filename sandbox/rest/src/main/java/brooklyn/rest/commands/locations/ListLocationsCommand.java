package brooklyn.rest.commands.locations;

import brooklyn.rest.api.LocationSummary;
import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class ListLocationsCommand extends BrooklynCommand {

  public ListLocationsCommand() {
    super("list-locations", "List all registered locations");
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) throws Exception {
    List<LocationSummary> locations = client.resource(uriFor("/v1/locations"))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<LocationSummary>>() {
        });
    for (LocationSummary summary : locations) {
      System.out.println(summary.toString());
    }
  }
}
