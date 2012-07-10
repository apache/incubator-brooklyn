package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.type.TypeReference;
import org.iq80.cli.Command;

import java.util.List;

@Command(name = "catalog-policies", description = "Prints the policies available on the server")
public class CatalogPoliciesCommand extends BrooklynCommand {

    @Override
    public Void call() throws Exception {

        // Common command behavior
        super.call();

        // Make an HTTP request to the REST server and get back a JSON encoded response
        String jsonResponse = httpClient
                .resource(endpoint + "/v1/catalog/policies")
                .accept("application/json")
                .get(ClientResponse.class)
                .getEntity(String.class);

        // Parse the JSON response
        List<String> policies = jsonParser.readValue(jsonResponse,new TypeReference<List<String>>(){});

        // Display the policies
        for (String policy : policies) {
            System.out.println(policy);
        }

        return null;
    }

}


