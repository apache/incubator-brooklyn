package brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;


public class Tomcat7SshDriver extends JavaWebAppSshDriver implements Tomcat7Driver {

    private String expandedInstallDir;

    public Tomcat7SshDriver(TomcatServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        return String.format("%s/logs/catalina.out",getRunDir());
    }

    protected String getDeploySubdir() {
       return "webapps";
    }

    protected Integer getShutdownPort() {
        return entity.getAttribute(TomcatServerImpl.SHUTDOWN_PORT);
    }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    @Override
    public void install() {
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName("apache-tomcat-"+getVersion());

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.downloadUrlAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add(format("tar xvzf %s",saveAs));

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
                body.append(
                "mkdir conf logs webapps temp",
                format("cp %s/conf/{server,web}.xml conf/",getExpandedInstallDir()),
                format("sed -i.bk s/8080/%s/g conf/server.xml",getHttpPort()),
                format("sed -i.bk s/8005/%s/g conf/server.xml",getShutdownPort()),
                "sed -i.bk /8009/D conf/server.xml"
        ).execute();

        ((TomcatServerImpl)entity).deployInitialWars();
    }

    @Override
    public void launch() {
        Map ports = MutableMap.of("httpPort",getHttpPort(), "jmxPort",getJmxPort(), "shutdownPort",getShutdownPort());

        Networking.checkPortsValid(ports);
        Map flags = MutableMap.of("usePidFile",false);

        // We wait for evidence of tomcat running because, using 
        // brooklyn.ssh.config.tool.class=brooklyn.util.internal.ssh.cli.SshCliTool,
        // we saw the ssh session return before the tomcat process was fully running 
        // so the process failed to start.
        newScript(flags, LAUNCHING).
        body.append(
                format("%s/bin/startup.sh >>$RUN/console 2>&1 </dev/null",getExpandedInstallDir()),
                "for i in {1..10}\n" +
                "do\n" +
                "    if [ -s "+getLogFileLocation()+" ]; then exit; fi\n" +
                "    sleep 1\n" +
                "done\n" +
            "echo \"Couldn't determine if tomcat-server is running (logs/catalina.out is still empty); continuing but may subsequently fail\""
        ).execute();
    }

    @Override
    public boolean isRunning() {
        Map flags = MutableMap.of("usePidFile","pid.txt");
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        Map flags = MutableMap.of("usePidFile","pid.txt");
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        Map flags = MutableMap.of("usePidFile","pid.txt");
        newScript(flags, KILLING).execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        List<String> options = new LinkedList<String>();
        options.addAll(super.getCustomJavaConfigOptions());
        options.add("-Xms200m");
        options.add("-Xmx800m");
        options.add("-XX:MaxPermSize=400m");
        return options;
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String,String> results = new LinkedHashMap<String,String>();
        results.putAll(super.getShellEnvironment());
        results.put("CATALINA_BASE",getRunDir());
        results.put("CATALINA_OPTS",results.get("JAVA_OPTS"));
        results.put("CATALINA_PID","pid.txt");
        results.put("RUN",getRunDir());
        return results;
    }
}
