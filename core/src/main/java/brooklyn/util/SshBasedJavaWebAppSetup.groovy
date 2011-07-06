package brooklyn.util

import java.io.File
import java.util.List;

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.basic.SshMachineLocation

public abstract class SshBasedJavaWebAppSetup extends SshBasedJavaAppSetup {

    protected String deployDir;
    
    public SshBasedJavaWebAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public SshBasedJavaWebAppSetup setDeployDir(String val) {
        this.deployDir = val
        return this
    }
    
    public abstract List<String> getDeployScript(String filename);

    /**
     * Copies f to loc:$deployDir and invokes this.getDeployScript
     * for further processing on server.
     */
    //TODO should take an input stream?
    public void deploy(File f) {
        String target = deployDir + "/" + f.getName()
        log.debug "Tomcat Deploy - Copying file {} to $machine {}", f.getAbsolutePath(), target
        int copySuccess = machine.copyTo f, target
        List<String> deployScript = getDeployScript(target)
        if (deployScript && !deployScript.isEmpty()) {
            int result = machine.run(out:System.out, deployScript)
            if (result) {
                log.error "Failed to deploy $f to $machine, result $result"
            } else {
                log.debug "Deployed $f to $machine"
            }
        }
    }
}
