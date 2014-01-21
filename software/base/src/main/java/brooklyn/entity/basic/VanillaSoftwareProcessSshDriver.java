package brooklyn.entity.basic;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

public class VanillaSoftwareProcessSshDriver extends AbstractSoftwareProcessSshDriver implements VanillaSoftwareProcessDriver {
        public VanillaSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        String downloadedFilename = null;
        
        public enum ArchiveType {
            TAR, TGZ, ZIP,
            UNKNOWN;
            public static VanillaSoftwareProcessSshDriver.ArchiveType of(String filename) {
                if (filename==null) return null;
                String fl = filename.toLowerCase();
                String ext = fl;
                if (ext.indexOf('.')>=0) ext = ext.substring(ext.lastIndexOf('.')+1);
                if ("zip".equals(ext)) return ZIP;
                if ("tar".equals(ext)) return TAR;
                if ("tgz".equals(ext) || fl.endsWith(".tar.gz") || fl.equals("tar.gz")) return TGZ;
                return UNKNOWN;
            }
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
                
                switch (ArchiveType.of(downloadedFilename)) {
                case TAR: 
                case TGZ: 
                    commands.add(BashCommands.INSTALL_TAR); 
                    break; 
                case ZIP: 
                    commands.add(BashCommands.INSTALL_UNZIP);
                    break;
                case UNKNOWN:
                    break;
                }

                newScript(INSTALLING).
                    failOnNonZeroResultCode().
                    // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                    environmentVariablesReset().
                    body.append(commands).execute();
            }
        }

        @Override
        public void customize() {
            if (downloadedFilename!=null) {
                List<String> commands = new LinkedList<String>();
                
                switch (ArchiveType.of(downloadedFilename)) {
                case TAR: 
                    commands.add("tar xvf "+Os.mergePathsUnix(getInstallDir(), downloadedFilename)); 
                    break; 
                case TGZ: 
                    commands.add("tar xvfz "+Os.mergePathsUnix(getInstallDir(), downloadedFilename)); 
                    break; 
                case ZIP: 
                    commands.add("unzip "+Os.mergePathsUnix(getInstallDir(), downloadedFilename)); 
                    break;
                case UNKNOWN:
                    commands.add("cp "+Os.mergePathsUnix(getInstallDir(), downloadedFilename)+" ."); 
                    break;
                }
                
                newScript(CUSTOMIZING).
                    failOnNonZeroResultCode().
                    // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                    environmentVariablesReset().
                    body.append(commands).execute();
            }
        }

        @Override
        public Map<String, String> getShellEnvironment() {
            return MutableMap.copyOf(super.getShellEnvironment()).add("PID_FILE", getPidFile());
        }
        
        public String getPidFile() {
            // TODO see note in VanillaSoftwareProcess about PID_FILE as a config key
//            if (getEntity().getConfigRaw(PID_FILE, includeInherited)) ...
            return Os.mergePathsUnix(getRunDir(), PID_FILENAME);
        }

        @Override
        public void launch() {
            newScript(LAUNCHING).
                failOnNonZeroResultCode().
                body.append(getEntity().getConfig(VanillaSoftwareProcess.LAUNCH_COMMAND)).
                execute();
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