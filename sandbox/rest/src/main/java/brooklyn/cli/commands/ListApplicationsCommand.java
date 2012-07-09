package brooklyn.cli.commands;

import brooklyn.rest.api.Application;
import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.type.TypeReference;
import org.iq80.cli.Command;

import java.util.List;

@Command(name = "list-applications", description = "List all registered applications")
public class ListApplicationsCommand extends BrooklynCommand {

    @Override
    public Void call() throws Exception {

        // Make an HTTP request to the REST server and get back a JSON encoded response
        String jsonResponse = httpClient
                .resource(endpoint + "/v1/applications")
                .accept("application/json")
                .get(ClientResponse.class)
                .getEntity(String.class);

        // Parse the JSON response
        List<Application> applications = jsonParser.readValue(jsonResponse,new TypeReference<List<Application>>(){});

        // Display the applications
        for (Application application : applications) {
            System.out.printf("%20s %10s\n",application.getSpec().getName(), application.getStatus());
        }

        return null;
    }
}


