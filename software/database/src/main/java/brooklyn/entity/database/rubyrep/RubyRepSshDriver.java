package brooklyn.entity.database.rubyrep;

import static java.lang.String.format;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.database.mysql.MySqlSshDriver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.ResourceUtils;

public class RubyRepSshDriver extends JavaSoftwareProcessSshDriver implements RubyRepDriver {

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);

    public RubyRepSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() {
        return getRunDir() + "/log/rubyrep.log";
    }

    @Override
    public void install() {
        List<String> commands = new LinkedList<String>();

        String saveAs = format("rubyrep-%s.zip", getVersion());
        // TODO for the time being it is hard coded download url since it isn't predictable
        commands.addAll(CommonCommands.downloadUrlAs("http://rubyforge.org/frs/download.php/74408/rubyrep-1.2.0.zip", getEntityVersionLabel("/"), saveAs));
        commands.add(CommonCommands.installExecutable("unzip"));
        commands.add("unzip " + saveAs);

        newScript(INSTALLING).failOnNonZeroResultCode().failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).failOnNonZeroResultCode()
                .body.append(format("cp -r %s/rubyrep-%s . || exit $!", getInstallDir(), getVersion())
        ).execute();
        try {
            customizeConfiguration();
        } catch (Exception e) {
            log.error("Failed to configure rubyrep, replication is unlikely to succeed", e);
        }
    }

    protected void customizeConfiguration() throws ExecutionException, InterruptedException, URISyntaxException {
        log.info("Copying creation script " + getEntity().toString());

        String configScriptUrl = entity.getConfig(RubyRepNode.CONFIGURATION_SCRIPT_URL);
        Reader configContents;
        if (configScriptUrl != null) {
            // If set accept as-is
            configContents = new InputStreamReader(new ResourceUtils(entity).getResourceFromUrl(configScriptUrl));
        } else {
            String configScriptContents = entity.getConfig(RubyRepNode.CONFIGURATION_CONTENTS);
            // If user has set config script leave as-is
            if (configScriptContents == null) {
                String left = entity.getConfig(RubyRepNode.LEFT_DATABASE_URL);
                String leftDb = entity.getConfig(RubyRepNode.LEFT_DATABASE);
                String right = entity.getConfig(RubyRepNode.RIGHT_DATABASE_URL);
                String rightDb = entity.getConfig(RubyRepNode.RIGHT_DATABASE);
                String leftUser = entity.getConfig(RubyRepNode.LEFT_USERNAME);
                String rightUser = entity.getConfig(RubyRepNode.RIGHT_USERNAME);
                String leftPass = entity.getConfig(RubyRepNode.LEFT_PASSWORD);
                String rightPass = entity.getConfig(RubyRepNode.RIGHT_PASSWORD);
                int replicationInterval = entity.getConfig(RubyRepNode.REPLICATION_INTERVAL);

                if (left == null || right == null) {
                    throw new UnsupportedOperationException("Please either set ruby rep configuration or left and right database");
                }

                String tables = entity.getConfig(RubyRepNode.TABLE_REGEXP);

                // TODO config.options
                configScriptContents =
                        "RR::Initializer::run do |config|\n" +
                                "config.left = " + toConfig(left, leftDb, leftUser, leftPass) +
                                " \n" +
                                "config.right =" + toConfig(right, rightDb, rightUser, rightPass) +
                                " \n" +
                                "config.include_tables /" + tables + "/\n" +
                                "config.options[:replication_interval] = " + replicationInterval + "\n" +
                                "config.options[:logged_replication_events] = [\n" +
                                ":all_changes, \n" +
                                ":all_conflicts\n" +
                                "]" +
                                "end";
            }

            configContents = new StringReader(configScriptContents);
        }

        log.info("Sending " + configContents);
        getMachine().copyTo(configContents, getRunDir() + "/myrubyrep.conf");
    }

    private String toConfig(String url, String db, String user, String pass) throws ExecutionException, InterruptedException, URISyntaxException {
        URI uri = new URI(url);
        return "{\n" +
                ":adapter  => '" + uri.getScheme() + "', \n" +
                ":database => '" + db + "',\n" +
                ":username => '" + user + "',\n" +
                ":password => '" + pass + "',\n" +
                ":host     => '" + uri.getHost() + "',\n" +
                ":port     => " + uri.getPort() + "\n" +
                "}\n";
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                .body.append(format("nohup rubyrep-%s/jruby/bin/jruby rubyrep-%s/bin/rubyrep replicate -c myrubyrep.conf > ./console 2>&1 &", getVersion(), getVersion())
        ).execute();
    }

    @Override
    public boolean isRunning() {
        Map flags = MutableMap.of("usePidFile", true);
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        Map flags = MutableMap.of("usePidFile", true);
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        Map flags = MutableMap.of("usePidFile", true);
        newScript(flags, KILLING).execute();
    }
}