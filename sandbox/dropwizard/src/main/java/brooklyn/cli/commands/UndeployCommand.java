package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.iq80.cli.Arguments;
import org.iq80.cli.Command;
import org.iq80.cli.Option;

import javax.ws.rs.core.MediaType;
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

    @Override
    public void run() throws Exception {

        // Make an HTTP request to the REST server
        WebResource webResource = getClient().resource(endpoint + "/v1/applications/" + app);
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON).delete(ClientResponse.class);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
            String err = getErrorMessage(clientResponse);
            throw new CommandExecutionException(err);
        }

        getOut().println("Application has been undeployed: " + app);
    }

}


