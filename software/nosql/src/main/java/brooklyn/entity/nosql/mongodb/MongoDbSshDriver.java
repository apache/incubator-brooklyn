package brooklyn.entity.nosql.mongodb;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.util.ssh.CommonCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MongoDbSshDriver extends AbstractSoftwareProcessSshDriver implements MongoDbDriver {

    public static final Logger log = LoggerFactory.getLogger(MongoDbSshDriver.class);

    private String expandedInstallDir;

    public MongoDbSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    public String getDataDirectory() {
        return entity.getConfig(MongoDbServer.DATA_DIRECTORY, getRunDir() + "/data");
    }

    @Override
    public void install() {
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(getBaseName());

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(urls, saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    @Override
    public void customize() {
        Map ports = ImmutableMap.of("port", getServerPort());
        NetworkUtils.checkPortsValid(ports);
        String command = String.format("mkdir -p %s", getDataDirectory());
        newScript(CUSTOMIZING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();

        String templateUrl = entity.getConfig(MongoDbServer.MONGODB_CONF_TEMPLATE_URL);
        if (!Strings.isNullOrEmpty(templateUrl)) copyTemplate(templateUrl, getConfFile());
    }

    @Override
    public void launch() {
        List<String> commands = new LinkedList<String>();
        Integer port = entity.getAttribute(MongoDbServer.PORT);

        ImmutableList.Builder<String> argsBuilder = ImmutableList.<String>builder()
                .add("--config", getConfFile())
                .add("--pidfilepath", getPidFile())
                .add("--dbpath", getDataDirectory())
                .add("--logpath", getLogFile())
                .add("--port", port.toString())
                .add("--fork");

        String replicaSetName = entity.getConfig(MongoDbServer.REPLICA_SET_NAME);
        if (!Strings.isNullOrEmpty(replicaSetName))
            argsBuilder.add("--replSet", replicaSetName);

        String args = Joiner.on(" ").join(argsBuilder.build());
        String command = String.format("%s/bin/mongod %s > out.log 2> err.log < /dev/null", getExpandedInstallDir(), args);
        commands.add(command);
        log.info(command);
        newScript(LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(commands).execute();

    }

    @Override
    public void stop() {
        Map flags = ImmutableMap.of("usePidFile", getPidFile());
        newScript(flags, STOPPING).execute();
    }

    @Override
    public boolean isRunning() {
        Map flags = ImmutableMap.of("usePidFile", getPidFile());
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }


    protected String getBaseName() {
        return getOsTag() + "-" + entity.getConfig(MongoDbServer.SUGGESTED_VERSION);
    }

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

    protected Integer getServerPort() {
        return entity.getAttribute(MongoDbServer.PORT);
    }

    private String getConfFile() {
        return getRunDir() + "/mongo.conf";
    }
}
