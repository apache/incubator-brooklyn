package brooklyn.cli.commands;

import brooklyn.rest.api.ApiError;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.Application.Status;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import com.google.common.collect.Sets;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.Client;
import org.iq80.cli.Command;
import org.iq80.cli.Option;
import org.iq80.cli.Arguments;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;

@Command(name = "deploy", description = "Deploys the specified application using given config, classpath, location, etc")
public class DeployCommand extends BrooklynCommand {

    @Option(name = "--format",
            description = "Either json,groovy,class, to force APP type detection")
    public String format;

    @Option(name = "--no-start",
            description = "Don't invoke `start` on the application")
    public boolean noStart = false;

    @Option(name = { "--location", "--locations" },
            title = "Location list",
            description = "Specifies the locations where the application will be launched. You can specify more than one location like this: \"loc1,loc2,loc3\"")
    public String locations;

    @Option(name = "--config",
            title = "Configuration parameters list",
            description = "Pass the config parameters to the application like this: \"A=B,C=D\"")
    public String config;

    @Option(name = "--classpath",
            description = "Upload the given classes")
    public String classpath;

    @Arguments(title = "APP",
            description = "where APP can be\n" +
                    "    * a fully qualified class-name of something on the classpath\n" +
                    "    * path or URL to a script file (if ends .groovy)\n" +
                    "    * path or URL to a JSON file (if ends .json)")
    public String app;

    @Override
    public Void call() throws Exception {

        // Common command behavior
        super.call();

        // Create Java object for request
        ApplicationSpec applicationSpec = new ApplicationSpec(
                app, // name
                Sets.newHashSet(new EntitySpec(app)), // entities
                Sets.newHashSet("/v1/locations/1") // locations
        );

        // Serialize the Java object to JSON
        String objectJsonString = jsonParser.writeValueAsString(applicationSpec);

        // Make an HTTP request to the REST server
        ClientResponse clientResponse = httpClient
                .resource(endpoint + "/v1/applications")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, objectJsonString);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.CREATED.getStatusCode()) {
            String response = clientResponse.getEntity(String.class);
            ApiError error = jsonParser.readValue(response, ApiError.class);
            System.err.println(error.getMessage());
            return null;
        }

        // Inform the user of the application location
        System.out.println("Starting at " + clientResponse.getLocation());

        // Check if application was  started successfully (via another REST call)
        Status status = getApplicationStatus(httpClient, clientResponse.getLocation());
        while (status != Application.Status.RUNNING && status != Application.Status.ERROR) {
            System.out.print(".");
            System.out.flush();
            Thread.sleep(1000);
        }
        if (status == Application.Status.RUNNING) {
            System.out.println("Done.");
        } else {
            System.err.println("Error.");
        }

        return null;
    }

    private Application.Status getApplicationStatus(Client client, URI uri) throws IOException {
        ClientResponse clientResponse = client
                .resource(uri)
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        String response = clientResponse.getEntity(String.class);
        Application application = jsonParser.readValue(response, Application.class);
        return application.getStatus();
    }

}


