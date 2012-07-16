package brooklyn.cli.commands;

import brooklyn.cli.HttpBroker;
import com.google.common.base.Objects;
import com.sun.jersey.api.client.Client;
import org.codehaus.jackson.map.ObjectMapper;
import org.iq80.cli.Option;
import org.iq80.cli.OptionType;
import org.iq80.cli.ParseException;
import java.util.concurrent.Callable;
import java.lang.UnsupportedOperationException;

public abstract class BrooklynCommand implements Callable<Void> {

    private static final Client httpClient = Client.create(); // Jersey rest client
    static final ObjectMapper jsonParser = new ObjectMapper(); // Jackson json parser

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
            name = { "--retry" },
            description = "Will retry connection to the endpoint for this time period, default \""+DEFAULT_RETRY_PERIOD+"s\"")
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
                .add("retry", retry)
                .add("no-retry",noRetry)
                .toString();
    }

    /**
     * This method should contain the particular behavior for each command
     * that extends {@link BrooklynCommand}.
     */
    public abstract void run() throws Exception;

    /**
     * This method contains the common behavior for any {@link BrooklynCommand} and
     * also makes a call to run() that contains the command-specific behavior.
     *
     * @return null
     * @throws Exception
     */
    public Void call() throws Exception {

        // Additional higher level syntax validation
        additionalValidation();

        // Embedded web server feature
        if(embedded)
            throw new UnsupportedOperationException(
                    "The \"--embedded\" option is not supported yet");

        // If --no-retry then set number of retries to 1
        if(noRetry)
            retry = 1;

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
     * Calling this will throw a {@link ParseException} that will be caught.
     *
     * @throws ParseException
     */
    private void additionalValidation() throws ParseException {
        // Make sure that "--retry" and "--no-retry" are mutually exclusive
        if(noRetry && retry!=DEFAULT_RETRY_PERIOD)
            throw new ParseException("The \"--retry\" and \"--no-retry\" options are mutually exclusive!");
    }

    HttpBroker getHttpBroker() {
        return new HttpBroker(httpClient, endpoint, retry);
    }

}

