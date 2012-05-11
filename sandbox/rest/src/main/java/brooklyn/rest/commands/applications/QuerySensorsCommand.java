package brooklyn.rest.commands.applications;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.api.SensorSummary;
import brooklyn.rest.commands.BrooklynCommand;
import static com.google.common.base.Preconditions.checkArgument;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.net.URI;
import java.util.Set;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class QuerySensorsCommand extends BrooklynCommand {

  public QuerySensorsCommand() {
    super("query-sensors", "Query all application sensors for all entities");
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
      queryAllSensors(client, summary.getLinks().get("sensors"));
      queryAllEntities(client, summary.getLinks().get("children"));
    }
  }

  private void queryAllSensors(JerseyClient client, URI sensorsUri) {
    Set<SensorSummary> sensors = client.resource(expandIfRelative(sensorsUri))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<Set<SensorSummary>>() {
        });
    for (SensorSummary summary : sensors) {
      String value = client.resource(expandIfRelative(summary.getLinks().get("self")))
          .type(MediaType.APPLICATION_JSON_TYPE).get(String.class);
      System.out.println("\t" + summary.getName() + " = " + value);
    }
    System.out.println();
  }
}
