package brooklyn.entity.webapp.jetty;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;

public class Jetty6SshDriver extends JavaWebAppSshDriver implements Jetty6Driver {

    public Jetty6SshDriver(Jetty6ServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        // TODO no wildcard
        return String.format("%s/logs/*.stderrout.log", getRunDir());
        // also, there is .requests.log
    }

    protected String getDeploySubdir() {
       return "webapps";
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName("jetty-"+getVersion()));

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_ZIP);
        commands.add("unzip "+saveAs);

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append(
                        // create app-specific dirs
                        "mkdir logs contexts webapps",
                        // link to the binary directories; silly that we have to do this but jetty has only one notion of "jetty.home" 
                        // (jetty.run is used only for writing the pid file, not for looking up webapps or even for logging)
                        format("for x in start.jar bin contrib modules lib extras; do ln -s %s/$x $x ; done", getExpandedInstallDir()),
                        // copy config
                        format("for x in etc resources; do cp -r %s/$x $x ; done", getExpandedInstallDir()),
                        // now modify the config file
                        format("sed -i.bk s/8080/%s/g etc/jetty.xml",getHttpPort()),
                        format("sed -i.bk s/8443/%s/g etc/jetty.xml",getHttpsPort())
                )
                .execute();

        getEntity().deployInitialWars();
    }

    @Override
    public void launch() {
        Map ports = MutableMap.of("httpPort", getHttpPort(), "jmxPort", getJmxPort());
        Networking.checkPortsValid(ports);

        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(
                        "./bin/jetty.sh start etc/jetty.xml etc/jetty-logging.xml etc/jetty-jmx.xml etc/jetty-stats.xml " +
                                ">> $RUN_DIR/console 2>&1 < /dev/null",
                        "for i in {1..10} ; do\n" +
                        "    if [ -s "+getLogFileLocation()+" ]; then exit; fi\n" +
                        "    sleep 1\n" +
                        "done",
                        "echo \"Couldn't determine if jetty-server is running (log file is still empty); continuing but may subsequently fail\""
                )
                .execute();
        log.debug("launched jetty");
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, "jetty.pid"), CHECK_RUNNING).execute() == 0;
    }

    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append("./bin/jetty.sh stop")
                .execute();
    }

    // not used, but an alternative to stop which might be useful
    public void kill1() {
        newScript(MutableMap.of(USE_PID_FILE, "jetty.pid"), STOPPING).execute();
    }

    public void kill9() {
        newScript(MutableMap.of(USE_PID_FILE, "jetty.pid"), KILLING).execute();
    }

    @Override
    public void kill() {
        kill9();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return MutableList.<String>builder()
                .addAll(super.getCustomJavaConfigOptions())
                .add("-Xms200m")
                .add("-Xmx800m")
                .add("-XX:MaxPermSize=400m")
                .build();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String,String>builder()
                .putAll(super.getShellEnvironment())
                .put("JETTY_RUN", getRunDir())
                .renameKey("JAVA_OPTS", "JAVA_OPTIONS")
                .build();
    }
}
