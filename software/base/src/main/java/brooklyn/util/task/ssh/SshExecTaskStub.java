package brooklyn.util.task.ssh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SshExecTaskStub {
    
    protected final List<String> commands = new ArrayList<String>();
    protected SshMachineLocation machine;
    
    // config data
    protected String summary;
    protected final ConfigBag config = ConfigBag.newInstance();
    
    public static enum ScriptReturnType { CUSTOM, EXIT_CODE, STDOUT_STRING, STDOUT_BYTES, STDERR_STRING, STDERR_BYTES }
    protected Function<SshExecTaskWrapper<?>, ?> returnResultTransformation = null;
    protected ScriptReturnType returnType = ScriptReturnType.EXIT_CODE;
    
    protected Boolean runAsScript = null;
    protected boolean runAsRoot = false;
    protected Boolean requireExitCodeZero = null;
    protected String extraErrorMessage = null;
    protected Map<String,String> shellEnvironment = new MutableMap<String, String>();
    protected final List<Function<SshExecTaskWrapper<?>, Void>> completionListeners = new ArrayList<Function<SshExecTaskWrapper<?>,Void>>();

    public SshExecTaskStub() {}
    
    protected SshExecTaskStub(SshExecTaskStub source) {
        commands.addAll(source.commands);
        machine = source.getMachine();
        summary = source.getSummary();
        config.copy(source.config);
        returnResultTransformation = source.returnResultTransformation;
        returnType = source.returnType;
        runAsScript = source.runAsScript;
        runAsRoot = source.runAsRoot;
        requireExitCodeZero = source.requireExitCodeZero;
        extraErrorMessage = source.extraErrorMessage;
        shellEnvironment.putAll(source.getShellEnvironment());
        completionListeners.addAll(source.completionListeners);
    }

    public String getSummary() {
        if (summary!=null) return summary;
        return Strings.join(commands, " ; ");
    }
    
    public SshMachineLocation getMachine() {
        return machine;
    }
    
    public Map<String, String> getShellEnvironment() {
        return ImmutableMap.copyOf(shellEnvironment);
    }
 
    @Override
    public String toString() {
        return super.toString()+"["+getSummary()+"]";
    }

    public List<String> getCommands() {
        return ImmutableList.copyOf(commands);
    }
 
    public List<Function<SshExecTaskWrapper<?>, Void>> getCompletionListeners() {
        return completionListeners;
    }
    
}
