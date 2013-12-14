package brooklyn.entity.database.rubyrep;

import static java.lang.String.format;

import java.io.Reader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.database.mysql.MySqlSshDriver;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableList;

public class RubyRepSshDriver extends JavaSoftwareProcessSshDriver implements RubyRepDriver {

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);

    private String expandedInstallDir;

    public RubyRepSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
    }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    @Override
    protected String getLogFileLocation() {
        return getRunDir() + "/log/rubyrep.log";
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName("rubrep");

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_UNZIP)
                .add("unzip " + saveAs)
                .build();

        newScript(INSTALLING).failOnNonZeroResultCode().body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).failOnNonZeroResultCode()
                .body.append(format("cp -R %s/rubyrep-%s .", getExpandedInstallDir(), getVersion()))
                .execute();
        try {
            customizeConfiguration();
        } catch (Exception e) {
            log.error("Failed to configure rubyrep, replication is unlikely to succeed", e);
        }
    }

    protected void customizeConfiguration() throws ExecutionException, InterruptedException, URISyntaxException {
        log.info("Copying creation script " + getEntity().toString());

        // TODO check these semantics are what we really want?
        String configScriptUrl = entity.getConfig(RubyRepNode.CONFIGURATION_SCRIPT_URL);
        Reader configContents;
        if (configScriptUrl != null) {
            // If set accept as-is
            configContents = Streams.reader(resource.getResourceFromUrl(configScriptUrl));
        } else {
            String configScriptContents = processTemplate(entity.getConfig(RubyRepNode.TEMPLATE_CONFIGURATION_URL));
            configContents = Streams.newReaderWithContents(configScriptContents);
        }

        log.info("Sending " + configContents);
        getMachine().copyTo(configContents, getRunDir() + "/rubyrep.conf");
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                .body.append(format("nohup rubyrep-%s/jruby/bin/jruby rubyrep-%s/bin/rubyrep replicate -c rubyrep.conf > ./console 2>&1 &", getVersion(), getVersion()))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", true), STOPPING).execute();
    }
}