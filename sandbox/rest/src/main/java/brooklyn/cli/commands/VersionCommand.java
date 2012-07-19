package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.iq80.cli.Command;
import javax.ws.rs.core.MediaType;

@Command(name = "version", description = "Print version")
public class VersionCommand extends BrooklynCommand {

    public void run() throws Exception {

        // Make an HTTP request to the REST server and get back a JSON encoded response
        WebResource webResource = getClient().resource(endpoint + "/v1/version");
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
        String jsonResponse = clientResponse.getEntity(String.class);

        // Parse the JSON response
        String version = getJsonParser().readValue(jsonResponse,String.class);

        getOut().println("Brooklyn version: "+version);
    }
}

