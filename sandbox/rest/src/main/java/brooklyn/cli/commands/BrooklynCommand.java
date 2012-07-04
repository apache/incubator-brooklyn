package brooklyn.cli.commands;

import com.google.common.base.Objects;
import org.iq80.cli.Help;
import org.iq80.cli.Option;
import org.iq80.cli.OptionType;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public abstract class BrooklynCommand implements Callable<Void> {

    @Inject
    public Help help;

    @Option(type = OptionType.GLOBAL,
            name = { "--embedded" },
            description = "Start a simple embedded local web server")
    public boolean embedded = false;

    @Option(type = OptionType.GLOBAL,
            name = { "--endpoint" },
            description = "REST endpoint, default \"http://localhost:8181\"")
    public String endpoint = "http://localhost:8181";

    @Option(type = OptionType.GLOBAL,
            name = { "--retry" },
            description = "Will retry connection to the endpoint for this time period, default \"30s\"")
    public int retry = 30;

    @Option(type = OptionType.GLOBAL,
            name = { "--no-retry" },
            description = "Won't retry connection")
    public boolean noRetry = false;

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("embedded", embedded)
                .add("endpoint", endpoint)
                .toString();
    }

}

