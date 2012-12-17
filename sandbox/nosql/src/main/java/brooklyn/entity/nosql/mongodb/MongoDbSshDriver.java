package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static brooklyn.entity.basic.lifecycle.CommonCommands.downloadUrlAs;

public class MongoDbSshDriver extends AbstractSoftwareProcessSshDriver implements MongoDbDriver {

    public static final Logger log = LoggerFactory.getLogger(MongoDbSshDriver.class);

    private static final String DOWNLOAD_ROOT = "http://fastdl.mongodb.org/";

    public MongoDbSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void install() {
        List<String> commands = new LinkedList<String>();
        String saveAs = "mongo.tar.gz";
        commands.addAll(downloadUrlAs(getUrl(), getEntityVersionLabel("/"), saveAs));
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
        String hostname = entity.getAttribute(SoftwareProcessEntity.HOSTNAME);
        String command = String.format("mkdir -p %s/data", getRunDir());
        newScript(CUSTOMIZING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();

        String url = entity.getConfig(MongoDbServer.CONFIG_URL);
        Reader configFile;
        if (!Strings.isNullOrEmpty(url))
            configFile = new InputStreamReader(new ResourceUtils(entity).getResourceFromUrl(url));
        else
            configFile = new StringReader("");
        getMachine().copyTo(configFile, getConfFile());
    }

    @Override
    public void launch() {
        List<String> commands = new LinkedList<String>();

        String command = String.format("%s/bin/mongod --config %s --pidfilepath %s --fork --dbpath %s --logpath %s > out.log 2> err.log < /dev/null",
                getMongoInstallDir(), getConfFile(), getPidFile(), getDataDir(), getLogFile());
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


    protected String getUrl() {
        // e.g. http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-2.2.2.tgz,
        // http://fastdl.mongodb.org/osx/mongodb-osx-x86_64-2.2.2.tgz
        // http://downloads.mongodb.org/win32/mongodb-win32-x86_64-1.8.5.zip
        // Note Windows download is a zip.
        String osDir = (getLocation().getOsDetails().isMac()) ? "osx/" : "linux/";
        return DOWNLOAD_ROOT + osDir + getBaseName() + ".tgz";
    }

    protected String getBaseName() {
        return getOsTag() + "-" + entity.getConfig(MongoDbServer.VERSION);
    }

    protected String getOsTag() {
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

    protected String getDataDir() {
        return getRunDir() + "/data/";
    }

    protected String getLogFile() {
        return getRunDir() + "/log.txt";
    }

    protected String getPidFile() {
        return getRunDir() + "/pid";
    }

    protected String getMongoInstallDir() {
        return getInstallDir() + "/" + getBaseName();
    }

    protected Integer getServerPort() {
        return entity.getAttribute(MongoDbServer.PORT);
    }

    private String getConfFile() {
        return getRunDir() + "/mongo.conf";
    }
}
