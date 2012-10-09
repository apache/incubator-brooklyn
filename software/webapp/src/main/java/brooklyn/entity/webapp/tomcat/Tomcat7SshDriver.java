package brooklyn.entity.webapp.tomcat;

import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;


public class Tomcat7SshDriver extends JavaWebAppSshDriver implements Tomcat7Driver {

    public Tomcat7SshDriver(TomcatServer entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        return String.format("%s/logs/catalina.out",getRunDir());
    }

    protected String getDeploySubdir() {
       return "webapps";
    }

    protected Integer getShutdownPort() {
        return entity.getAttribute(TomcatServer.SHUTDOWN_PORT);
    }

    //@Override
    //public void postLaunch() {
    //    entity.setAttribute(TomcatServer.SHUTDOWN_PORT, getShutdownPort());
    //    super.postLaunch();
    //}

    @Override
    public void install() {
        String url = "http://download.nextag.com/apache/tomcat/tomcat-7/v"+getVersion()+"/bin/apache-tomcat-"+getVersion()+".tar.gz";
        String saveAs = "apache-tomcat-"+getVersion()+".tar.gz";

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel("/"), saveAs));
        commands.add(CommonCommands.installExecutable("tar"));
        commands.add(format("tar xvzf %s",saveAs));

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
                body.append(
                format("mkdir -p %s",getRunDir()),
                format("cd %s",getRunDir()),
                "mkdir conf logs webapps temp",
                format("cp %s/apache-tomcat-%s/conf/{server,web}.xml conf/",getInstallDir(),getVersion()),
                format("sed -i.bk s/8080/%s/g conf/server.xml",getHttpPort()),
                format("sed -i.bk s/8005/%s/g conf/server.xml",getShutdownPort()),
                "sed -i.bk /8009/D conf/server.xml"
        ).execute();

        ((TomcatServer)entity).deployInitialWars();
    }

    @Override
    public void launch() {
        Map ports = MutableMap.of("httpPort",getHttpPort(), "jmxPort",getJmxPort(), "shutdownPort",getShutdownPort());

        NetworkUtils.checkPortsValid(ports);
        Map flags = MutableMap.of("usePidFile",false);

        newScript(flags, LAUNCHING).
        body.append(
                format("%s/apache-tomcat-%s/bin/startup.sh >>$RUN/console 2>&1 </dev/null",getInstallDir(),getVersion())
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
