package brooklyn.rest.commands.locations;

import brooklyn.rest.api.LocationSummary;
import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.PrintStream;
import java.util.List;

public class ListLocationsCommand extends BrooklynCommand {

  public ListLocationsCommand() {
    super("list-locations", "List all registered locations");
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    List<LocationSummary> locations = client.resource(uriFor("/v1/locations"))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<LocationSummary>>() {
        });
    for (LocationSummary summary : locations) {
      out.println(summary.toString());
    }
  }
}
