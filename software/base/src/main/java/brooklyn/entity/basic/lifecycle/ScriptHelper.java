package brooklyn.entity.basic.lifecycle;

import static java.lang.String.format;
import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class ScriptHelper {

    public static final Logger log = LoggerFactory.getLogger(ScriptHelper.class);

    protected final ScriptRunner runner;
    public final String summary;

    public final ScriptPart header = new ScriptPart(this);
    public final ScriptPart body = new ScriptPart(this);
    public final ScriptPart footer = new ScriptPart(this);
    
    protected final Map flags = new LinkedHashMap();
    protected Predicate<? super Integer> resultCodeCheck = Predicates.alwaysTrue();
    protected Predicate<? super ScriptHelper> executionCheck = Predicates.alwaysTrue();
    
    protected boolean gatherOutput = false;
    protected ByteArrayOutputStream stdout, stderr;

    public ScriptHelper(ScriptRunner runner, String summary) {
        this.runner = runner;
        this.summary = summary;
    }

    /**
     * Takes a closure which accepts this ScriptHelper and returns true or false
     * as to whether the script needs to run (or can throw error if desired)
     */
    public ScriptHelper executeIf(Closure c) {
        Predicate<ScriptHelper> predicate = GroovyJavaMethods.predicateFromClosure(c);
        return executeIf(predicate);
    }

    public ScriptHelper executeIf(Predicate<? super ScriptHelper> c) {
        executionCheck = c;
        return this;
    }

    public ScriptHelper skipIfBodyEmpty() {
        Predicate<ScriptHelper> p = new Predicate<ScriptHelper>() {
            @Override
            public boolean apply(ScriptHelper input) {
                return !input.body.isEmpty();
            }
        };

        return executeIf(p);
    }

    public ScriptHelper failIfBodyEmpty() {
        Predicate<ScriptHelper> p = new Predicate<ScriptHelper>() {
            @Override
            public boolean apply(ScriptHelper input) {
                if (input.body.isEmpty()) {
                    throw new IllegalStateException("body empty for " + summary);
                }
                return true;
            }
        };

        return executeIf(p);
    }

    public ScriptHelper failOnNonZeroResultCode(boolean val) {
        if (val) {
            failOnNonZeroResultCode();
        } else {
            requireResultCode(Predicates.alwaysTrue());
        }
        return this;
    }

    public ScriptHelper failOnNonZeroResultCode() {
        return updateTaskAndFailOnNonZeroResultCode();
    }

    public ScriptHelper failOnNonZeroResultCodeWithoutUpdatingTask() {
        requireResultCode(Predicates.equalTo(0));
        return this;
    }
    
    public ScriptHelper updateTaskAndFailOnNonZeroResultCode() {
        gatherOutput();
        // a failure listener would be a cleaner way

        resultCodeCheck = new Predicate<Integer>() {
            @Override
            public boolean apply(@Nullable Integer input) {
                if (input==0) return true;

                try {
                    String notes = "";
                    if (!getResultStderr().isEmpty())
                        notes += "STDERR\n" + getResultStderr()+"\n";
                    if (!getResultStdout().isEmpty())
                        notes += "\n" + "STDOUT\n" + getResultStdout()+"\n";
                    Tasks.setExtraStatusDetails(notes.trim());
                } catch (Exception e) {
                    log.warn("Unable to collect additional metadata on failure of "+summary+": "+e);
                }

                return false;
            }
        };
        
        return this;
    }
    
    /**
     * Convenience for error-checking the result.
     * <p/>
     * Takes closure which accepts bash exit code (integer),
     * and returns false if it is invalid. Default is that this resultCodeCheck
     * closure always returns true (and the exit code is made available to the
     * caller if they care)
     */
    public ScriptHelper requireResultCode(Closure integerFilter) {
        Predicate<Integer> objectPredicate = GroovyJavaMethods.predicateFromClosure(integerFilter);
        return requireResultCode(objectPredicate);
    }

    public ScriptHelper requireResultCode(Predicate<? super Integer> integerFilter) {
        resultCodeCheck = integerFilter;
        return this;
    }

    protected Runnable mutexAcquire = new Runnable() {
        public void run() {
        }
    };

    protected Runnable mutexRelease = new Runnable() {
        public void run() {
        }
    };

    /**
     * indicates that the script should acquire the given mutexId on the given mutexSupport
     * and maintain it for the duration of script execution;
     * typically used to prevent parallel scripts from conflicting in access to a resource
     * (e.g. a folder, or a config file used by a process)
     */
    public ScriptHelper useMutex(final WithMutexes mutexSupport, final String mutexId, final String description) {
        mutexAcquire = new Runnable() {
            public void run() {
                try {
                    mutexSupport.acquireMutex(mutexId, description);
                } catch (InterruptedException e) {
                    throw new RuntimeInterruptedException(e);
                }
            }
        };

        mutexRelease = new Runnable() {
            public void run() {
                mutexSupport.releaseMutex(mutexId);
            }
        };

        return this;
    }

    public ScriptHelper gatherOutput() {
        return gatherOutput(true);
    }
    public ScriptHelper gatherOutput(boolean gather) {
        gatherOutput = gather;
        return this;
    }
    
    public int execute() {
        if (DynamicTasks.getTaskQueuingContext()!=null) {
            return new DynamicTasks.AutoQueue<Integer>("ssh ("+summary+")") { protected Integer main() {
                return executeInternal();
            }}.get(); 
        } else {
            return executeInternal();
        }
    }
    public int executeInternal() {
        if (!executionCheck.apply(this)) {
            return 0;
        }

        List<String> lines = getLines();
        if (log.isTraceEnabled()) log.trace("executing: {} - {}", summary, lines);
        
        int result;
        try {
            mutexAcquire.run();
            Map flags = getFlags();
            if (gatherOutput) {
                if (stdout==null) stdout = new ByteArrayOutputStream();
                if (stderr==null) stderr = new ByteArrayOutputStream();
                flags.put("out", stdout);
                flags.put("err", stderr);
            }
            result = runner.execute(flags, lines, summary);
        } catch (RuntimeInterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(format("Execution failed, invocation error for %s: %s", summary, e.getMessage()), e);
        } finally {
            mutexRelease.run();
        }
        if (log.isTraceEnabled()) log.trace("finished executing: {} - result code {}", summary, result);
        
        if (!resultCodeCheck.apply(result)) {
            throw new IllegalStateException(format("Execution failed, invalid result %s for %s", result, summary));
        }
        return result;
    }

    public Map getFlags() {
        return flags;
    }
    
    public ScriptHelper setFlag(String flag, Object value) {
        flags.put(flag, value);
        return this;
    }
    
    public <T> ScriptHelper setFlag(ConfigKey<T> flag, T value) {
        return setFlag(flag.getName(), value);
    }
    
    public List<String> getLines() {
        List<String> result = new LinkedList<String>();
        result.addAll(header.lines);
        result.addAll(body.lines);
        result.addAll(footer.lines);
        return result;
    }
    
    public String getResultStdout() {
        if (stdout==null) throw new IllegalStateException("output not available on "+this+"; ensure gatherOutput(true) is set");
        return stdout.toString();
    }
    public String getResultStderr() {
        if (stderr==null) throw new IllegalStateException("output not available on "+this+"; ensure gatherOutput(true) is set");
        return stderr.toString();
    }

}
