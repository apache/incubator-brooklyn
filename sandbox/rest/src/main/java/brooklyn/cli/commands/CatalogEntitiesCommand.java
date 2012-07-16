package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.type.TypeReference;
import org.iq80.cli.Command;
import java.util.List;

@Command(name = "catalog-entities", description = "Prints the entities available on the server")
public class CatalogEntitiesCommand extends BrooklynCommand {

    public void run() throws Exception {

        // Make an HTTP request to the REST server and get back a JSON encoded response
        ClientResponse clientResponse = getHttpBroker().getWithRetry("/v1/catalog/entities");
        String jsonResponse = clientResponse.getEntity(String.class);

        // Parse the JSON response
        List<String> entities = jsonParser.readValue(jsonResponse,new TypeReference<List<String>>(){});

        // Display the entities
        for (String entity : entities) {
            getOut().println(entity);
        }

    }

}


