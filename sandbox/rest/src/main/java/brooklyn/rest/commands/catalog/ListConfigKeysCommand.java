package brooklyn.rest.commands.catalog;

import brooklyn.rest.commands.BrooklynCommand;
import static com.google.common.base.Preconditions.checkArgument;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class ListConfigKeysCommand extends BrooklynCommand {

  public ListConfigKeysCommand() {
    super("config-keys", "List config keys for entity type");
  }

  @Override
  public String getSyntax() {
    return "[options] <entity type>";
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "The type of the entity is mandatory");

    String type = (String) params.getArgList().get(0);
    List<String> keys = client.resource(uriFor("/v1/catalog/entities/" + type))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<String>>() {
        });

    for (String key : keys) {
      System.out.println(key);
    }
  }
}
