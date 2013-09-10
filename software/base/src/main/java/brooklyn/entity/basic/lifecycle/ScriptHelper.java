package brooklyn.entity.basic.lifecycle;

import static java.lang.String.format;
import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.management.TaskQueueingContext;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class ScriptHelper {

    public static final Logger log = LoggerFactory.getLogger(ScriptHelper.class);

    protected final NaiveScriptRunner runner;
    public final String summary;

    public final ScriptPart header = new ScriptPart(this);
    public final ScriptPart body = new ScriptPart(this);
    public final ScriptPart footer = new ScriptPart(this);
    
    protected final Map flags = new LinkedHashMap();
    protected Predicate<? super Integer> resultCodeCheck = Predicates.alwaysTrue();
    protected Predicate<? super ScriptHelper> executionCheck = Predicates.alwaysTrue();
    
    protected boolean isTransient = false;
    protected boolean gatherOutput = false;
    protected ByteArrayOutputStream stdout, stderr;
    protected Task<Integer> task;

    public ScriptHelper(NaiveScriptRunner runner, String summary) {
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
    
    /** indicates explicitly that the task can be safely forgotten about after it runs; useful for things like
     * check_running which run repeatedly */
    public void setTransient() {
        isTransient = true;
    }

    /** creates a task which will execute this script; note this can only be run once per instance of this class */
    public synchronized Task<Integer> newTask() {
        if (task!=null) throw new IllegalStateException("task can only be generated once");
        TaskBuilder<Integer> tb = Tasks.<Integer>builder().name("ssh: "+summary).body(
                new Callable<Integer>() {
                    public Integer call() throws Exception {
                        return executeInternal();
                    }
                });
        
        try {
            ByteArrayOutputStream stdin = new ByteArrayOutputStream();
            for (String line: getLines()) {
                stdin.write(line.getBytes());
                stdin.write("\n".getBytes());
            }
            tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDIN, stdin));
        } catch (IOException e) {
            log.warn("Error registering stream "+BrooklynTasks.STREAM_STDIN+" on "+tb+": "+e, e);
        }
        if (gatherOutput) {
            stdout = new ByteArrayOutputStream();
            tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDOUT, stdout));
            stderr = new ByteArrayOutputStream();
            tb.tag(BrooklynTasks.tagForStream(BrooklynTasks.STREAM_STDERR, stderr));
        }
        task = tb.build();
        if (isTransient) BrooklynTasks.setTransient(task);
        return task;
    }
    
    /** returns the task, if it has been constructed, or null; use {@link #newTask()} to build 
     * (if it is null and you need a task) */
    public Task<Integer> peekTask() {
        return task;
    }

    /** queues the task for execution if we are in a {@link TaskQueueingContext} (e.g. EffectorTaskFactory); 
     * or if we aren't in a queueing context, it will submit the task (assuming there is an {@link ExecutionContext}
     * _and_ block until completion, throwing on error */
    @Beta
    public Task<Integer> queue() {
        return DynamicTasks.queueIfPossible(newTask()).orSubmitAndBlock().getTask();
    }
    
    public int execute() {
        if (DynamicTasks.getTaskQueuingContext()!=null) {
            return queue().getUnchecked();
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
            throw logWithDetailsAndThrow(format("Execution failed, invocation error for %s: %s", summary, e.getMessage()), e);
        } catch (Exception e) {
            throw logWithDetailsAndThrow(format("Execution failed, invocation error for %s: %s", summary, e.getMessage()), e);
        } finally {
            mutexRelease.run();
        }
        if (log.isTraceEnabled()) log.trace("finished executing: {} - result code {}", summary, result);
        
        if (!resultCodeCheck.apply(result)) {
            throw logWithDetailsAndThrow(format("Execution failed, invalid result %s for %s", result, summary), null);
        }
        return result;
    }

    protected RuntimeException logWithDetailsAndThrow(String message, Throwable optionalCause) {
        log.warn(message+" (throwing)");
        Streams.logStreamTail(log, "STDERR of problem in "+Tasks.current(), stderr, 1024);
        Streams.logStreamTail(log, "STDOUT of problem in "+Tasks.current(), stdout, 1024);
        Streams.logStreamTail(log, "STDIN of problem in "+Tasks.current(), Streams.byteArrayOfString(Strings.join(getLines(),"\n")), 4096);
        if (optionalCause!=null) throw new IllegalStateException(message, optionalCause);
        throw new IllegalStateException(message);
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
    
    /** ensures the script runs with no environment variables; by default they will be inherited */
    public ScriptHelper environmentVariablesReset() {
        return environmentVariablesReset(MutableMap.of());
    }
    
    /** overrides the default environment variables to use the given set; by default they will be inherited.
     * TODO would be nice to have a way to add just a few, but there is no way currently to access the
     * getShellEnvironment() from the driver which is what gets inherited (at execution time) */
    public ScriptHelper environmentVariablesReset(Map<?,?> envVarsToSet) {
        setFlag("env", envVarsToSet);
        return this;
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
