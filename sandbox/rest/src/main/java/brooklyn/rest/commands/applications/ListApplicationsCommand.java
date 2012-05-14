package brooklyn.rest.commands.applications;

import brooklyn.rest.api.Application;
import brooklyn.rest.commands.BrooklynCommand;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class ListApplicationsCommand extends BrooklynCommand {

  public ListApplicationsCommand() {
    super("list-applications", "List all registered applications");
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) throws Exception {
    List<Application> applications = client.resource(uriFor("/v1/applications"))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<Application>>() {
        });

    String tableFormat = "%20s %10s\n";
    System.out.printf(tableFormat, "Application", "Status");

    for (Application application : applications) {
      System.out.printf(tableFormat, application.getSpec().getName(), application.getStatus());
    }
  }
}
