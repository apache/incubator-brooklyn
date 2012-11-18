package brooklyn.rest.domain;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

public class ScriptExecutionSummary {

    @JsonSerialize(include=Inclusion.NON_NULL)
    private final Object result;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String problem;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String stdout;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String stderr;
    
    public ScriptExecutionSummary(
            @JsonProperty("result") Object result, 
            @JsonProperty("problem") String problem, 
            @JsonProperty("stdout") String stdout, 
            @JsonProperty("stderr") String stderr) {
        super();
        this.result = result;
        this.problem = problem;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public Object getResult() {
        return result;
    }

    public String getProblem() {
        return problem;
    }

    public String getStderr() {
        return stderr;
    }
    
    public String getStdout() {
        return stdout;
    }
    
}
