package brooklyn.rest.commands;

import brooklyn.rest.api.Application;
import static brooklyn.rest.api.Application.Status;
import brooklyn.rest.api.ApplicationSpec;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.io.File;
import java.net.URI;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class StartApplicationCommand extends BrooklynCommand {

  public StartApplicationCommand() {
    super("start-application", "Start a new application from a JSON spec file.");
  }

  @Override
  public String getSyntax() {
    return "[options] <json file>";
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) {
    URI endpoint = URI.create(params.getOptionValue("endpoint", "http://localhost:8080") + "/v1/applications");

    try {
      if (params.getArgList().size() < 1)
        throw new RuntimeException("Path to JSON file is mandatory.");

      String jsonFileName = (String) params.getArgList().get(0);
      ApplicationSpec spec = json.readValue(new File(jsonFileName), ApplicationSpec.class);

      ClientResponse response = client.post(endpoint, MediaType.APPLICATION_JSON_TYPE,
          spec, ClientResponse.class);

      System.out.println("Starting at " + response.getLocation());

      Status status;
      do {
        System.err.print(".");
        Thread.sleep(1000);

        status = getApplicationStatus(client, response.getLocation());
      } while (status != Status.RUNNING && status != Status.ERROR);

      if (status == Status.RUNNING) {
        System.out.println("Done.");
      } else {
        System.out.println("Error.");
      }

    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  private Status getApplicationStatus(JerseyClient client, URI uri) {
    Application application = client.get(uri, MediaType.APPLICATION_JSON_TYPE, Application.class);
    return application.getStatus();
  }
}
