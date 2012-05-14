package brooklyn.rest.commands.applications;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.EffectorSummary;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.commands.BrooklynCommand;
import static com.google.common.base.Preconditions.checkArgument;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.net.URI;
import java.util.Set;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class ListEffectorsCommand extends BrooklynCommand {

  public ListEffectorsCommand() {
    super("list-effectors", "List all effectors for all entities for a given application");
  }

  @Override
  public String getSyntax() {
    return "[options] <application name>";
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Application name is mandatory");

    String name = (String) params.getArgList().get(0);
    Application application = client.resource(uriFor("/v1/applications/" + name))
        .type(MediaType.APPLICATION_JSON_TYPE).get(Application.class);

    queryAllEntities(client, application.getLinks().get("entities"));
  }

  private void queryAllEntities(JerseyClient client, URI resource) {
    Set<EntitySummary> entities = client.resource(expandIfRelative(resource))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<Set<EntitySummary>>() {
        });

    for (EntitySummary summary : entities) {
      System.out.println(summary.getLinks().get("self") + " #" + summary.getType());
      queryListOfEffectors(client, summary.getLinks().get("effectors"));
      queryAllEntities(client, summary.getLinks().get("children"));
    }
  }

  private void queryListOfEffectors(JerseyClient client, URI effectorsUri) {
    Set<EffectorSummary> effectors = client.resource(expandIfRelative(effectorsUri))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<Set<EffectorSummary>>() {
        });
    for (EffectorSummary summary : effectors) {
      System.out.println("\t" + summary.getReturnType() + " " +
          summary.getName() + " " + summary.getParameters());
      System.out.println("\t" + summary.getLinks().get("self") + "\n");
    }
  }
}
