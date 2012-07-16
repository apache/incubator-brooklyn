package brooklyn.cli.commands;

import brooklyn.rest.api.ApiError;
import com.sun.jersey.api.client.ClientResponse;
import org.iq80.cli.Arguments;
import org.iq80.cli.Command;
import org.iq80.cli.Option;
import javax.ws.rs.core.Response;

@Command(name = "undeploy", description = "Undeploys the specified application")
public class UndeployCommand extends BrooklynCommand {

    @Option(name = "--no-stop",
            description = "Don't invoke `stop` on the application")
    public boolean noStart = false;

    @Arguments(title = "APP",
            description = "where APP can be\n" +
                    "    * a fully qualified class-name of something on the classpath\n" +
                    "    * path or URL to a script file (if ends .groovy)\n" +
                    "    * path or URL to a JSON file (if ends .json)")
    public String app;

    public void run() throws Exception {

        // Make an HTTP request to the REST server
        ClientResponse clientResponse = getHttpBroker().deleteWithRetry("/v1/applications/" + app);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
            String response = clientResponse.getEntity(String.class);
            ApiError error = jsonParser.readValue(response, ApiError.class);
            System.err.println(error.getMessage());
            return;
        }

        // Looks like all was ok, so will inform the user
        System.out.println("Application has been undeployed: " + app);

        return;
    }

}


