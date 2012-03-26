package brooklyn.util.internal.duplicates;

public class ExecResult {
    
    private final String stdout;
    private final String stderr;
    private final int exitval;
    
    ExecResult(String stdout, String stderr, int exitval) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitval = exitval;
    }
    
    public String getStdout() {
        return stdout;
    }
    
    public String getStderr() {
        return stderr;
    }
    
    public int getExitval() {
        return exitval;
    }
    
    @Override
    public String toString() {
        return "exitcode="+exitval+"; stdout="+stdout.trim()+"; stderr="+stderr.trim();
    }
}