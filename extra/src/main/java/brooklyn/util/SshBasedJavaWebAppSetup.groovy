package brooklyn.util

import java.io.File
import java.util.List

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.basic.SshMachineLocation

public abstract class SshBasedJavaWebAppSetup extends SshBasedJavaAppSetup {
    protected String deployDir
    protected int httpPort
    
    public SshBasedJavaWebAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public void setDeployDir(String val) {
        deployDir = val
    }
    
    public void setHttpPort(int val) {
        httpPort = val
    }

    @Override
    protected void setEntityAttributes() {
        super.setEntityAttributes()
        def host = entity.getAttribute(Attributes.HOSTNAME)
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        entity.setAttribute(JavaWebApp.ROOT_URL, "http://${host}:${httpPort}/")
    }

    /**
     * Copy a file to the {@link #runDir} on the server.
     *
     * @return The location of the file on the server
     */
    public String copy(File file) {
        String target = runDir + "/" + file.name
        log.info "Deploying file {} to {} on {}", file.name, target, machine
        try {
            machine.copyTo file, target
        } catch (IOException ioe) {
            log.error "Failed to copy {} to {}: {}", file.name, machine, ioe.message
            throw new IllegalStateException("Failed to copy ${file.name} to ${machine}", ioe)
        }
        return target
    }

    /**
     * Copies a file to the server and invokes {@link #getDeployScript()}
     * for further processing.
     */
    public void deploy(File file) {
        String target = copy(file)
        List<String> deployScript = getDeployScript(target)
        if (deployScript && !deployScript.isEmpty()) {
            int result = machine.run(out:System.out, deployScript)
            if (result != 0) {
                log.error "Failed to deploy {} on {}, result {}", file.name, machine, result
	            throw new IllegalStateException("Failed to deploy ${file.name} on ${machine}")
            } else {
                log.debug "Deployed {} on {}", file.name, machine
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
