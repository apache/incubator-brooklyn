package brooklyn.entity.database.postgresql;

import static brooklyn.entity.basic.lifecycle.CommonCommands.alternatives;
import static brooklyn.entity.basic.lifecycle.CommonCommands.dontRequireTtyForSudo;
import static brooklyn.entity.basic.lifecycle.CommonCommands.file;
import static brooklyn.entity.basic.lifecycle.CommonCommands.installPackage;
import static brooklyn.entity.basic.lifecycle.CommonCommands.sudo;
import static java.lang.String.format;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * The SSH implementation of the {@link PostgreSqlDriver}.
 */

public class PostgreSqlSshDriver extends AbstractSoftwareProcessSshDriver
        implements PostgreSqlDriver {

    public static final Logger log = LoggerFactory
            .getLogger(PostgreSqlSshDriver.class);


    private final Iterable<String> pgctlLocations = ImmutableList.of(
            "/usr/lib/postgresql/9.*/bin/",
            "/usr/lib/postgresql/8.*/bin/",
            "/opt/local/lib/postgresql9*/bin/",
            "/opt/local/lib/postgresql8*/bin/",
            "/usr/local/bin/",
            "/usr/bin/",
            "/bin/"
    );

    public PostgreSqlSshDriver(PostgreSqlNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void install() {        
        // Check we can actually find a usable pg_ctl
        Collection<String> pgbinlocator = ImmutableList.copyOf(Iterables.transform(pgctlLocations, new Function<String, String>() {
            @Override
            public String apply(@Nullable String s) {
                return "test -f " + s + "pg_ctl";
            }
        }));
        // Link to correct binaries folder (different versions of pg_ctl and psql don't always play well together)
        Collection<String> pgctlLinker = ImmutableList.copyOf(Iterables.transform(pgctlLocations, new Function<String, String>() {
            @Override
            public String apply(@Nullable String s) {
                return file(s + "pg_ctl", "ln -s " + s + " bin");
            }
        }));

        // TODO tied to version 9.1 for port installs
        newScript(INSTALLING).body.append(
                dontRequireTtyForSudo(),
                alternatives(pgbinlocator,
                        installPackage(ImmutableMap.of("yum", "postgresql postgresql-server", "port", "postgresql91 postgresql91-server"), "postgresql")),
                alternatives(pgctlLinker, "echo \"WARNING: failed to locate postgresql binaries, will likely fail subsequently\""))
                .failOnNonZeroResultCode().execute();
    }

    public static String sudoAsUserAppendCommandOutputToFile(String user, String commandWhoseOutputToWrite, String file) {
        return format("( %s | sudo -E -n -u %s -s -- tee -a %s )",
                commandWhoseOutputToWrite, user, file);
    }

    public static String sudoAsUser(String user, String command) {
        if (command == null) return null;
        return format("( sudo -E -n -u %s -s -- %s )", user, command);
    }

    @Override
    public void customize() {
        // Some OSes start postgres during package installation
        newScript(CUSTOMIZING).body.append(sudoAsUser("postgres", "/etc/init.d/postgresql stop")).execute();
        newScript(CUSTOMIZING).body.append(
                sudo("mkdir -p " + getDataDir()),
                sudo("chown postgres:postgres " + getDataDir()),
                sudo("touch " + getLogFile()),
                sudo("chown postgres:postgres " + getLogFile()),
                sudoAsUser("postgres", getInstallDir() + "/bin/initdb -D " + getDataDir()),
                sudoAsUserAppendCommandOutputToFile("postgres", "echo \"listen_addresses = '*'\"", getDataDir() + "/postgresql.conf"),
                // TODO give users control which hosts can connect and the authentication mechanism
                sudoAsUserAppendCommandOutputToFile("postgres", "echo \"host    all         all         0.0.0.0/0             md5\"", getDataDir() + "/pg_hba.conf")
        ).failOnNonZeroResultCode().execute();

        customizeUserCreationScript();

        /*
        * Try establishing an external connection. If you get a "Connection refused...accepting TCP/IP connections on port 5432?" error then the port is probably closed. 
        * Check that the firewall allows external TCP/IP connections (netstat -nap). You can open a port with lokkit or by configuring the iptables.
        */
    }

    protected void customizeUserCreationScript() {
        log.info("Copying creation script " + getEntity().toString());
        String creationScriptUrl = entity.getConfig(PostgreSqlNode.CREATION_SCRIPT_URL);
        Reader creationScript;
        if (creationScriptUrl != null)
            creationScript = new InputStreamReader(new ResourceUtils(entity).getResourceFromUrl(creationScriptUrl));
        else creationScript = new StringReader(entity.getConfig(PostgreSqlNode.CREATION_SCRIPT_CONTENTS));

        getMachine().copyTo(creationScript, getRunDir() + "/creation-script.sql");

        newScript(CUSTOMIZING).body.append(callPgctl("start", true),
                sudoAsUser("postgres", getInstallDir() + "/bin/psql --file " + getRunDir() + "/creation-script.sql"),
                callPgctl("stop", true)).
                failOnNonZeroResultCode().execute();
    }

    protected String getDataDir() {
        return getRunDir() + "/data";
    }

    protected String getLogFile() {
        return getRunDir() + "/postgresql.log";
    }


    protected String callPgctl(String command, boolean waitForIt) {
        return sudoAsUser("postgres", getInstallDir() + "/bin/pg_ctl -D " + getDataDir()
                + " -l " + getLogFile() + (waitForIt ? " -w " : " ") + command);
    }

    @Override
    public void launch() {
        log.info(String.format("Starting entity %s at %s", this, getLocation()));
        newScript(LAUNCHING).body.append(callPgctl("start", false)).failOnNonZeroResultCode().execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(CHECK_RUNNING).body.append(callPgctl("status", false)).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(STOPPING).body.append(callPgctl("stop", false)).failOnNonZeroResultCode().execute();
    }

    @Override
    public PostgreSqlNodeImpl getEntity() {
        return (PostgreSqlNodeImpl) super.getEntity();
    }
}
