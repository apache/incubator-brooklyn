package brooklyn.cli.commands;

/**
 * Exception that can happen during the execution of a {@link BrooklynCommand}
 */
public class CommandExecutionException extends Exception {

    CommandExecutionException(String s) {
        super(s);
    }

    CommandExecutionException(String s, Throwable cause) {
        super(s, cause);
    }
}
