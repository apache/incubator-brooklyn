package brooklyn.rest.commands.catalog;

import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.PrintStream;
import java.util.List;

public class ListCatalogEntitiesCommand extends BrooklynCommand {

  public ListCatalogEntitiesCommand() {
    super("catalog-entities", "List all the entities from the catalog");
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params
  ) throws Exception {
    List<String> entities = client.resource(uriFor("/v1/catalog/entities"))
      .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<String>>() {
      });
    for (String entity : entities) {
      out.println(entity);
    }
  }
}
