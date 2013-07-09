package brooklyn.cli.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import org.codehaus.jackson.map.ObjectMapper;
import org.iq80.cli.Option;
import org.iq80.cli.OptionType;
import org.iq80.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.rest.domain.ApiError;

import com.google.common.base.Objects;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import java.lang.UnsupportedOperationException;

public abstract class BrooklynCommand implements Callable<Void> {

    public static final Logger LOG = LoggerFactory.getLogger(BrooklynCommand.class);

    private PrintStream out = System.out;
    private PrintStream err = System.err;

    private Client httpClient = null; // Jersey REST client
    private ObjectMapper jsonParser = null; // Jackson json parser

    public static final int DEFAULT_RETRY_PERIOD = 30;
    public static final String DEFAULT_ENDPOINT = "http://localhost:8080";

    @Option(type = OptionType.GLOBAL,
            name = { "--embedded" },
            description = "Start a simple embedded local web server")
    public boolean embedded = false;

    @Option(type = OptionType.GLOBAL,
            name = { "--endpoint" },
            description = "REST endpoint, default \""+DEFAULT_ENDPOINT+"\"")
    public String endpoint = DEFAULT_ENDPOINT;


    @Option(type = OptionType.GLOBAL,
            name = {"--user"},
            description = "User name")
    public String user = null;

    @Option(type = OptionType.GLOBAL,
            name = {"--password"},
            description = "User password")
    public String password = null;

    @Option(type = OptionType.GLOBAL,
            name = { "--retry" },
            description = "Will retry connection to the endpoint for this time period, " +
                "default \""+DEFAULT_RETRY_PERIOD+"s\"")
    public int retry = DEFAULT_RETRY_PERIOD;

    @Option(type = OptionType.GLOBAL,
            name = { "--no-retry" },
            description = "Won't retry connection")
    public boolean noRetry = false;

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("embedded", embedded)
                .add("endpoint", endpoint)
                .add("user", user)
                .add("retry", retry)
                .add("no-retry",noRetry)
                .toString();
    }

    /**
     * Executes behaviour specific to this command.
     * Called by the framework after initializing all of the CLI option fields.
     */
    public abstract void run() throws Exception;

    /**
     * Contains the common behavior for any {@link BrooklynCommand} and
     * also makes a call to run() that contains the command-specific behavior.
     *
     * @return null
     * @throws Exception
     */
    @Override
    public Void call() throws Exception {

        // Additional higher level syntax validation
        additionalValidation();

        // Embedded web server feature
        if(embedded) {
            throw new UnsupportedOperationException(
                    "The \"--embedded\" option is not supported yet");
        }

        // Number of retries should be zero if noRetry; number of "tries" would be 1
        if(noRetry) {
            retry = 0;
        }

        // Execute the command-specific code
        run();

        return null;
    }

    /**
     * Adds some additional validation for the cli arguments.
     *
     * Git-like-cli doesn't attempt to do this so this is the place
     * where some of the additional things that we need can go.
     *
     * Calling this could throw a {@link ParseException}
     *
     * @throws ParseException
     */
    private void additionalValidation() throws ParseException {
        // Make sure that "--retry" and "--no-retry" are mutually exclusive
        if(noRetry && retry!=DEFAULT_RETRY_PERIOD)
            throw new ParseException("The \"--retry\" and \"--no-retry\" options are mutually exclusive");
    }

    /**
     * Get an instancse of the http client
     *
     * @return a fully configured retry-aware Jersey client
     */
    Client getClient() {
        if(httpClient==null) {

            // Lazily create a Jersey client instance
            httpClient = Client.create();

            // Add a retry filter that retries the request every second for a given number of attempts
            httpClient.addFilter(new ClientFilter() {
                @Override
                public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
                    ClientHandlerException lasterror = null;
                    int i = 0;
                    int maxAttempts = retry+1; // "--retry" option affects this
                    do {
                        i++;
                        try {
                            return getNext().handle(cr);
                        } catch (ClientHandlerException e) {
                            lasterror = e;
                            if (i < maxAttempts) {
                                LOG.debug("Request failed, retry attempt "+i+" of "+retry, e);
                                getErr().println("Request failed ("+e.getCause()+"); retry attempt "+i+" of "+retry+" ...");

                                try {
                                    Thread.sleep(1000);
                                } catch(InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new ClientHandlerException("Interrupted; aborting request retries", ie);
                                }
                            }
                        }
                    } while (i < maxAttempts);

                    throw lasterror;
                }
            });

            if (user !=null && password != null) {
              httpClient.addFilter(new HTTPBasicAuthFilter(user, password));
            }

            // Add a Jersey GZIP filter
            httpClient.addFilter(new GZIPContentEncodingFilter(true));
        }
        return httpClient;
    }

    ObjectMapper getJsonParser() {
        if(jsonParser==null) {
            // Lazily create a Jackson JSON parser
            jsonParser = new ObjectMapper();
        }
        return jsonParser;
    }

    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    protected String getErrorMessage(ClientResponse clientResponse) {
        String response = clientResponse.getEntity(String.class);
        try {
            // Try to see if the server responded with an error message from the API
            ApiError error = getJsonParser().readValue(response, ApiError.class);
            return error.getMessage();
        } catch (IOException e) {
            // If not, inform the user of the underlying response (e.g. if server threw NPE or whatever)
            int statusCode = clientResponse.getStatus();
            ClientResponse.Status status = clientResponse.getClientResponseStatus();
            String responseText = clientResponse.getEntity(String.class);
            return "Server returned "+status+"("+statusCode+"); "+responseText;
        }
    }

}

