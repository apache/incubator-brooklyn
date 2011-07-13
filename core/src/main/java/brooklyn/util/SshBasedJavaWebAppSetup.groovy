package brooklyn.util

import java.io.File
import java.util.List;

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.basic.SshMachineLocation

public abstract class SshBasedJavaWebAppSetup extends SshBasedJavaAppSetup {
    protected String deployDir
    protected int httpPort
    
    public SshBasedJavaWebAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public SshBasedJavaWebAppSetup setDeployDir(String val) {
        deployDir = val
        return this
    }
    
    public SshBasedJavaWebAppSetup setHttpPort(int val) {
        httpPort = val
        return this
    }

    /**
     * Copies a file to the {@link #runDir} and invokes {@link #getDeployScript()}
     * for further processing on server.
     */
    public void deploy(File f) {
        String target = runDir + "/" + f.name
        log.debug "WebApp Deploy - Copying file {} to {}:{}", f.absolutePath, machine, target
        int copySuccess = machine.copyTo f, target
        if (!copySuccess) {
            log.error "Failed to copy {} to {}, result {}", f.name, machine, copySuccess
            throw new IllegalStateException("Failed to transfer ${f.name} to ${machine}")
        }
        List<String> deployScript = getDeployScript(target)
        if (deployScript && !deployScript.isEmpty()) {
            int result = machine.run(out:System.out, deployScript)
            if (result) {
                log.error "Failed to deploy {} to {}, result {}", f.name, machine, result
            } else {
                log.debug "Deployed {} to {}", f.name, machine
            }
        }
    }

    /**
     * Deploy the file at the specified location on the server.
     */
    public List<String> getDeployScript(String locOnServer) {
        List<String> script = [
            "[ -f ${locOnServer} ] || exit 1",
            "cp ${locOnServer} ${deployDir}",
        ]
        return script
    }
}
