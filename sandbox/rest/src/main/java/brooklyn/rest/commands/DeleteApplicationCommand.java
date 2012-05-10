package brooklyn.rest.commands;

import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.net.URI;
import org.apache.commons.cli.CommandLine;

public class DeleteApplicationCommand extends BrooklynCommand {

  public DeleteApplicationCommand() {
    super("delete-application", "Delete application by name");
  }

  @Override
  public String getSyntax() {
    return "[options] <application name>";
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) {
    try {
      if (params.getArgList().size() < 1)
        throw new RuntimeException("Application name is mandatory.");

      String name = (String) params.getArgList().get(0);
      URI endpoint = URI.create(params.getOptionValue("endpoint",
          "http://localhost:8080") + "/v1/applications/" + name);

      ClientResponse response = client.delete(endpoint, ClientResponse.class);
      System.out.println("Ok, status: " + response.getStatus());

    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }
}
