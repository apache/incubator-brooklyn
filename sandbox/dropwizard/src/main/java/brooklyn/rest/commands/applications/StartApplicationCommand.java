package brooklyn.rest.commands.applications;

import brooklyn.rest.commands.BrooklynCommand;
import brooklyn.rest.domain.ApiError;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.Status;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;

public class StartApplicationCommand extends BrooklynCommand {

  public StartApplicationCommand() {
    super("start-application", "Start a new application from a JSON spec file.");
  }

  @Override
  public String getSyntax() {
    return "[options] <json file>";
  }

  @Override
  public Options getOptions() {
    return super.getOptions()
        .addOption("n", "name", true, "Override application name");
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Path to JSON file is mandatory");

    String jsonFileName = (String) params.getArgList().get(0);
    ApplicationSpec spec = json.readValue(new File(jsonFileName), ApplicationSpec.class);

    if (params.hasOption("name")) {
      spec = ApplicationSpec.builder().from(spec).name(params.getOptionValue("name")).build();
    }

    ClientResponse response = client.resource(uriFor("/v1/applications"))
        .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, spec);

    if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
      ApiError error = response.getEntity(ApiError.class);
      throw new RuntimeException(error.getMessage());
    }

    out.println("Starting at " + response.getLocation());

    Status status;
    do {
      out.print(".");
      out.flush();
      Thread.sleep(1000);

      status = getApplicationStatus(client, response.getLocation());
    } while (status != Status.RUNNING && status != Status.ERROR);

    if (status == Status.RUNNING) {
      out.println("Done.");
    } else {
      err.println("Error.");
    }
  }

  private Status getApplicationStatus(Client client, URI uri) {
    ApplicationSummary application = client.resource(uri)
        .type(MediaType.APPLICATION_JSON_TYPE).get(ApplicationSummary.class);
    return application.getStatus();
  }
}
