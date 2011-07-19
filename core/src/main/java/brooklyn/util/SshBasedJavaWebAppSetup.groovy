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
        log.debug "Deploying file {} to {} on {}", f.name, target, machine
        try {
	        machine.copyTo f, target
        } catch (IOException ioe) {
            log.error "Failed to copy {} to {}: {}", f.name, machine, ioe.message
            throw new IllegalStateException("Failed to copy ${f.name} to ${machine}", ioe)
        }
        List<String> deployScript = getDeployScript(target)
        if (deployScript && !deployScript.isEmpty()) {
            int result = machine.run(out:System.out, deployScript)
            if (result != 0) {
                log.error "Failed to deploy {} on {}, result {}", f.name, machine, result
	            throw new IllegalStateException("Failed to deploy ${f.name} on ${machine}")
            } else {
                log.debug "Deployed {} on {}", f.name, machine
            }
        }
    }

    /**
     * Deploy the file found at the specified location on the server.
     *
     * Checks that the file exists, and fails if not accessible, otherwise copies it
     * to the configured deploy directory. This is required because exit status from
     * the Jsch scp command is not reliable.
     */
    public List<String> getDeployScript(String locOnServer) {
        List<String> script = [
            "test -f ${locOnServer} || exit 1",
            "cp ${locOnServer} ${deployDir}",
        ]
        return script
    }
}
