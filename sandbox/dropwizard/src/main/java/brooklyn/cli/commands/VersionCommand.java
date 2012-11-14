package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.iq80.cli.Command;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Command(name = "version", description = "Print version")
public class VersionCommand extends BrooklynCommand {

    @Override
    public void run() throws Exception {

        // Make an HTTP request to the REST server and get back a JSON encoded response
        WebResource webResource = getClient().resource(endpoint + "/v1/version");
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            String err = getErrorMessage(clientResponse);
            throw new CommandExecutionException(err);
        }

        // Parse the JSON response
        String jsonResponse = clientResponse.getEntity(String.class);
        String version = getJsonParser().readValue(jsonResponse,String.class);

        getOut().println("Brooklyn version: "+version);
    }
}

