package brooklyn.entity.database.derby;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;

/**
 * Start a {@link DerbyDatabase} in a {@link Location} accessible over ssh.
 *
 * TODO work in progress
 */
public class DerbyDatabaseSshDriver extends JavaSoftwareProcessSshDriver implements DerbyDatabaseDriver {

    // TOD Previous comment said "JMX is configured using command line switch"; how should that be set here?

    public DerbyDatabaseSshDriver(DerbyDatabase entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public String getPidFile() { return "derby.pid"; }

    @Override
    protected String getLogFileLocation() {
        throw new UnsupportedOperationException("Work in progress");
    }

    @Override
    public void install() {
        String url = format("http://www.mirrorservice.org/sites/ftp.apache.org/db/derby/db-derby-%s/db-derby-%s-lib.tar.gz", getVersion(), getVersion());
        String saveAs = format("db-derby-%s-lib.tar.gz", getVersion());

        List<String> commands = ImmutableList.<String>builder()
                .addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel("/"), saveAs))
                .add(CommonCommands.INSTALL_TAR)
                .add("tar xzfv " + saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        format("cp -R %s/derby-broker-%s/{bin,etc,lib} .", getInstallDir(), getVersion()),
                        "make install PREFIX="+getRunDir())
                .execute();
    }
    
    @Override
    public void launch() {
        // TODO Should we redirect stdout/stderr: format(" >> %s/console 2>&1 </dev/null &", getRunDir())
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append("nohup ./bin/derby &")
                .execute();
    }
 

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING)
                .execute() == 0;
    }

    /**
     * Restarts redis with the current configuration.
     */
    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", getPidFile()), STOPPING)
                .execute();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String, String> orig = super.getShellEnvironment();
        
        return MutableMap.<String, String>builder()
                .putAll(orig)
                .put("DERBY_HOME", getRunDir())
                .put("DERBY_WORK", getRunDir())
                .putIfNotNull("DERBY_OPTS", orig.get("JAVA_OPTS"))
                .build();
    }
}
