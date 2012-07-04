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

}

