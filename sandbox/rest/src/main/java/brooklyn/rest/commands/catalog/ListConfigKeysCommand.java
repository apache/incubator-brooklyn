package brooklyn.rest.commands.catalog;

import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.PrintStream;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class ListConfigKeysCommand extends BrooklynCommand {

  public ListConfigKeysCommand() {
    super("config-keys", "List config keys for entity type");
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
    List<String> keys = client.resource(uriFor("/v1/catalog/entities/" + type))
      .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<String>>() {
      });

    for (String key : keys) {
      out.println(key);
    }
  }
}
