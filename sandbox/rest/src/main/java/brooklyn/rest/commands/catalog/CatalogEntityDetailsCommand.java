package brooklyn.rest.commands.catalog;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.PrintStream;

import javax.ws.rs.core.MediaType;

import org.apache.commons.cli.CommandLine;

import brooklyn.rest.api.CatalogEntitySummary;
import brooklyn.rest.commands.BrooklynCommand;

import com.sun.jersey.api.client.Client;
import com.yammer.dropwizard.json.Json;

public class CatalogEntityDetailsCommand extends BrooklynCommand {

  public CatalogEntityDetailsCommand() {
    super("catalog-entity", "Show details of entity type");
  }

  @Override
  public String getSyntax() {
    return "[options] <entity type>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "The type of the entity is mandatory");

    String type = (String) params.getArgList().get(0);
    CatalogEntitySummary catalogEntity = client.resource(uriFor("/v1/catalog/entities/" + type))
            .type(MediaType.APPLICATION_JSON_TYPE).get(CatalogEntitySummary.class);
    out.println(catalogEntity);
  }
}
