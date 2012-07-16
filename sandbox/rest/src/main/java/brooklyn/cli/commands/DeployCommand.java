package brooklyn.cli.commands;

import brooklyn.rest.api.ApiError;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.Application.Status;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import org.iq80.cli.Command;
import org.iq80.cli.Option;
import org.iq80.cli.Arguments;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;

@Command(name = "deploy", description = "Deploys the specified application using given config, classpath, location, etc")
public class DeployCommand extends BrooklynCommand {

    private static final String JSON_FORMAT = "json";
    private static final String GROOVY_FORMAT = "groovy";
    private static final String CLASS_FORMAT = "class";

    @Option(name = "--format",
            allowedValues = {JSON_FORMAT, GROOVY_FORMAT, CLASS_FORMAT},
            description = "Either "+JSON_FORMAT+","+GROOVY_FORMAT+","+CLASS_FORMAT+", to force APP type detection")
    public String format = CLASS_FORMAT;

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

    public void run() throws Exception {

        // Throw exception for unsupported options
        if(noStart)
            throw new UnsupportedOperationException(
                    "The \"--no-start\" option is not supported yet");
        if(locations!=null)
            throw new UnsupportedOperationException(
                    "The \"--location\",\"--locations\" options are not supported yet");
        if(config!=null)
            throw new UnsupportedOperationException(
                    "The \"--config\" option is not supported yet");

        // Upload the groovy app to the server if provided
        if(format.equals(GROOVY_FORMAT)){

            // Inform the user that we are loading the application to the server
            System.out.println("Loading groovy script to the server: "+app);

            // Get the user's groovy script
            String groovyScript = Joiner
                    .on("\n")
                    .join(Files.readLines(
                            new File(app),Charset.forName("utf-8")));

            // Make an HTTP request to the REST server
            String jsonEncodedGroovyScript = jsonParser.writeValueAsString(groovyScript); //encode the script to a JSON string
            ClientResponse clientResponse = getHttpBroker().postWithRetry("/v1/catalog",jsonEncodedGroovyScript);

            // Make sure we get the correct HTTP response code
            if (clientResponse.getStatus() != Response.Status.CREATED.getStatusCode()) {
                String response = clientResponse.getEntity(String.class);
                ApiError error = jsonParser.readValue(response, ApiError.class);
                System.err.println(error.getMessage());
                return;
            }

            // Get the catalog entity name that was just created
            String locationPath = clientResponse.getLocation().getPath();
            String entityName = locationPath.substring(locationPath.lastIndexOf("/")+1);

            // Inform the user about the new catalog name for the app
            System.out.println("Application has been added to the server's catalog: "+entityName);

            // Next stage assumes that app is the catalog name
            app = entityName;

        }

        // Create the JSON request object
        String objectJsonString;
        if(format.equals(JSON_FORMAT)){
            // Inform the user that we are loading the JSON provided in the file
            System.out.println("Loading json request object form file: "+app);
            // Load the JSON from the file
            objectJsonString = Files.toString(new File(app),Charset.forName("utf-8"));;
        } else { // CLASS_FORMAT
            // Create Java object for request
            ApplicationSpec applicationSpec = new ApplicationSpec(
                    app, // name
                    Sets.newHashSet(new EntitySpec(app)), // entities
                    Sets.newHashSet("/v1/locations/1") // locations
            );
            // Serialize the Java object to JSON
            objectJsonString = jsonParser.writeValueAsString(applicationSpec);
        }

        // Make an HTTP request to the REST server
        ClientResponse clientResponse = getHttpBroker().postWithRetry("/v1/applications",objectJsonString);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.CREATED.getStatusCode()) {
            String response = clientResponse.getEntity(String.class);
            ApiError error = jsonParser.readValue(response, ApiError.class);
            System.err.println(error.getMessage());
            return;
        }

        // Inform the user of the application location
        System.out.println("Starting at " + clientResponse.getLocation());

        // Check if application was  started successfully (via another REST call)
        Status status = getApplicationStatus(clientResponse.getLocation());
        while (status != Application.Status.RUNNING && status != Application.Status.ERROR) {
            System.out.print(".");
            System.out.flush();
            Thread.sleep(1000);
            status = getApplicationStatus(clientResponse.getLocation());
        }
        if (status == Application.Status.RUNNING) {
            System.out.println("Done.");
        } else {
            System.err.println("Error.");
        }

        return;
    }

    private Application.Status getApplicationStatus(URI uri) throws IOException, InterruptedException {
        ClientResponse clientResponse = getHttpBroker().getWithRetry(uri.getPath().toString());
        String response = clientResponse.getEntity(String.class);
        Application application = jsonParser.readValue(response, Application.class);
        return application.getStatus();
    }

}


