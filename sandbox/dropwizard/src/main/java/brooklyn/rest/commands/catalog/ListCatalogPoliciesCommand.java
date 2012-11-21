package brooklyn.rest.commands.catalog;

import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.PrintStream;
import java.util.List;

public class ListCatalogPoliciesCommand extends BrooklynCommand {

  public ListCatalogPoliciesCommand() {
    super("catalog-policies", "List all the policies from the catalog");
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    List<String> policies = client.resource(uriFor("/v1/catalog/policies"))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<String>>() {
        });
    for (String policy : policies) {
      out.println(policy);
    }
  }
}
