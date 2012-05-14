package brooklyn.rest.commands.catalog;

import brooklyn.rest.commands.BrooklynCommand;
import com.google.common.base.Joiner;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.io.Files;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.json.Json;
import java.io.File;
import java.nio.charset.Charset;
import javax.ws.rs.core.MediaType;
import org.apache.commons.cli.CommandLine;

public class LoadClassCommand extends BrooklynCommand {

  public LoadClassCommand() {
    super("load-class", "Load code from external groovy files.");
  }

  @Override
  public String getSyntax() {
    return "[options] <groovy file>";
  }

  @Override
  protected void run(Json json, JerseyClient client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Path to Groovy file is mandatory.");

    String scriptFileName = (String) params.getArgList().get(0);
    String groovyScript = Joiner.on("\n").join(Files.readLines(new File(scriptFileName),
        Charset.forName("utf-8")));

    ClientResponse response = client.resource(uriFor("/v1/catalog"))
        .type(MediaType.APPLICATION_JSON_TYPE).post(ClientResponse.class, groovyScript);

    System.out.println("Ok, create: " + response.getLocation());
  }
}
