package brooklyn.cli.commands;

import brooklyn.rest.api.Application;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.codehaus.jackson.type.TypeReference;
import org.iq80.cli.Command;

import javax.ws.rs.core.MediaType;
import java.util.List;

@Command(name = "list-applications", description = "List all registered applications")
public class ListApplicationsCommand extends BrooklynCommand {

    public void run() throws Exception {

        // Make an HTTP request to the REST server and get back a JSON encoded response
        WebResource webResource = getClient().resource(endpoint + "/v1/applications");
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
        String jsonResponse = clientResponse.getEntity(String.class);

        // Parse the JSON response
        List<Application> applications = getJsonParser().readValue(jsonResponse, new TypeReference<List<Application>>() {
        });

        // Display the applications
        for (Application application : applications) {
            getOut().printf("%20s %10s\n",application.getSpec().getName(), application.getStatus());
        }

    }
}


