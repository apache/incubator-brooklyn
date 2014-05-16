package brooklyn.entity.basic;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;

public class VanillaSoftwareProcessSshDriver extends AbstractSoftwareProcessSshDriver implements VanillaSoftwareProcessDriver {

    public VanillaSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    String downloadedFilename = null;

    /** needed because the download url might be different! */
    @Override
    protected String getInstallLabelExtraSalt() {
        Maybe<Object> url = getEntity().getConfigRaw(SoftwareProcess.DOWNLOAD_URL, true);
        if (url.isAbsent()) return null;
        // TODO a user-friendly hash would be nice, but tricky since we don't want it to be too long or contain path chars
        return Identifiers.makeIdFromHash( url.get().hashCode() );
    }
    
    @Override
    public void install() {
        Maybe<Object> url = getEntity().getConfigRaw(SoftwareProcess.DOWNLOAD_URL, true);
        if (url.isPresentAndNonNull()) {
            DownloadResolver resolver = Entities.newDownloader(this);
            List<String> urls = resolver.getTargets();
            downloadedFilename = resolver.getFilename();

            List<String> commands = new LinkedList<String>();
            commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, downloadedFilename));
            commands.addAll(ArchiveUtils.installCommands(downloadedFilename));

            int result = newScript(INSTALLING)
                    .failOnNonZeroResultCode(false)
                    // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                    .environmentVariablesReset()
                    .body.append(commands)
                    .execute();
            
            if (result!=0) {
                // could not install at remote machine; try resolving URL here and copying across
                for (String urlI: urls) {
                    result = ArchiveUtils.install(getMachine(), urlI, Urls.mergePaths(getInstallDir(), downloadedFilename));
                    if (result==0) 
                        break;
                }
                if (result != 0) 
                    throw new IllegalStateException("Error installing archive: " + downloadedFilename);
            }
        }
    }

    @Override
    public void customize() {
        if (downloadedFilename != null) {
            newScript(CUSTOMIZING)
                    .failOnNonZeroResultCode()
                    // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                    .environmentVariablesReset()
                    .body.append(ArchiveUtils.extractCommands(downloadedFilename, getInstallDir()))
                    .execute();
        }
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.copyOf(super.getShellEnvironment()).add("PID_FILE", getPidFile());
    }

    public String getPidFile() {
        // TODO see note in VanillaSoftwareProcess about PID_FILE as a config key
        // if (getEntity().getConfigRaw(PID_FILE, includeInherited)) ...
        return Os.mergePathsUnix(getRunDir(), PID_FILENAME);
    }

    @Override
    public void launch() {
        newScript(LAUNCHING)
            .failOnNonZeroResultCode()
            .body.append(getEntity().getConfig(VanillaSoftwareProcess.LAUNCH_COMMAND))
            .execute();
    }

    @Override
    public boolean isRunning() {
        // TODO custom
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute()==0;
    }

    @Override
    public void stop() {
        // TODO custom
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();
    }
}
