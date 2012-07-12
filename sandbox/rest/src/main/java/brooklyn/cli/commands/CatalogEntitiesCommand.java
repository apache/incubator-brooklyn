package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.type.TypeReference;
import org.iq80.cli.Command;
import java.util.List;

@Command(name = "catalog-entities", description = "Prints the entities available on the server")
public class CatalogEntitiesCommand extends BrooklynCommand {

    @Override
    public Void call() throws Exception {

        // Common command behavior
        super.call();

        // Make an HTTP request to the REST server and get back a JSON encoded response
        ClientResponse clientResponse = getHttpBroker().getWithRetry("/v1/catalog/entities");
        String jsonResponse = clientResponse.getEntity(String.class);

        // Parse the JSON response
        List<String> entities = jsonParser.readValue(jsonResponse,new TypeReference<List<String>>(){});

        // Display the entities
        for (String entity : entities) {
            System.out.println(entity);
        }

        return null;
    }

}


