package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.codehaus.jackson.type.TypeReference;
import org.iq80.cli.Command;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Command(name = "catalog-policies", description = "Prints the policies available on the server")
public class CatalogPoliciesCommand extends BrooklynCommand {

    @Override
    public void run() throws Exception {

        // Make an HTTP request to the REST server and get back a JSON encoded response
        WebResource webResource = getClient().resource(endpoint + "/v1/catalog/policies");
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            String err = getErrorMessage(clientResponse);
            throw new CommandExecutionException(err);
        }

        // Parse the JSON response
        String jsonResponse = clientResponse.getEntity(String.class);
        List<String> policies = getJsonParser().readValue(jsonResponse, new TypeReference<List<String>>() {});

        // Display the policies
        for (String policy : policies) {
            getOut().println(policy);
        }

    }

}


