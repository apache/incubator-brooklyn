package brooklyn.entity.nosql.mongodb;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableMap;

public abstract class AbstractMongoDBSshDriver extends AbstractSoftwareProcessSshDriver {

    public AbstractMongoDBSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(getBaseName()));
    
        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);
    
        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }
    
    @Override
    public boolean isRunning() {
        Map<String,?> flags = ImmutableMap.of("usePidFile", getPidFile());
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }

    protected String getBaseName() {
        return getOsTag() + "-" + entity.getConfig(AbstractMongoDBServer.SUGGESTED_VERSION);
    }

    // IDE note: This is used by MongoDBServer.DOWNLOAD_URL
    public String getOsDir() {
        return (getLocation().getOsDetails().isMac()) ? "osx" : "linux";
    }

    public String getOsTag() {
        OsDetails os = getLocation().getOsDetails();
        if (os == null) {
            // Default to generic linux
            return "mongodb-linux-x86_64";
        } else if (os.isMac()) {
            // Mac is 64bit only
            return "mongodb-osx-x86_64";
        } else {
            String arch = os.is64bit() ? "x86_64" : "i686";
            return "mongodb-linux-" + arch;
        }
    }

    protected String getLogFile() {
        return getRunDir() + "/log.txt";
    }

    protected String getPidFile() {
        return getRunDir() + "/pid";
    }

    /**
     * Kills the server with SIGINT. Sending SIGKILL is likely to resuult in data corruption.
     * @see <a href="http://docs.mongodb.org/manual/tutorial/manage-mongodb-processes/#sending-a-unix-int-or-term-signal">http://docs.mongodb.org/manual/tutorial/manage-mongodb-processes/#sending-a-unix-int-or-term-signal</a>
     */
    @Override
    public void stop() {
        // We could also use SIGTERM (15)
        new ScriptHelper(this, "Send SIGINT to MongoDB server")
                .body.append("kill -2 $(cat " + getPidFile() + ")")
                .execute();
    }

}