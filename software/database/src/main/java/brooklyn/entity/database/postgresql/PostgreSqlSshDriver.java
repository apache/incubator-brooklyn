/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.database.postgresql;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.Strings;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static brooklyn.util.ssh.BashCommands.*;
import static brooklyn.util.ssh.BashCommands.chainGroup;
import static java.lang.String.format;

/**
 * The SSH implementation of the {@link PostgreSqlDriver}.
 */
public class PostgreSqlSshDriver extends AbstractSoftwareProcessSshDriver implements PostgreSqlDriver {

   public static final Logger log = LoggerFactory.getLogger(PostgreSqlSshDriver.class);

   public PostgreSqlSshDriver(PostgreSqlNodeImpl entity, SshMachineLocation machine) {
      super(entity, machine);

      entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFile());
   }

   @Override
   public void install() {
      String version = getEntity().getConfig(SoftwareProcess.SUGGESTED_VERSION);
      String majorMinorVersion = version.substring(0, version.lastIndexOf("-"));
      String shortVersion = majorMinorVersion.replace(".", "");

      Iterable<String> pgctlLocations = ImmutableList.of(
              "/usr/lib/postgresql/"+majorMinorVersion+"/bin/",
              "/opt/local/lib/postgresql"+shortVersion+"/bin/",
              "/usr/pgsql-"+majorMinorVersion+"/bin",
              "/usr/local/bin/",
              "/usr/bin/",
              "/bin/");

      DynamicTasks.queueIfPossible(SshTasks.dontRequireTtyForSudo(getMachine(),
              // sudo is absolutely required here, in customize we set user to postgres
              true)).orSubmitAndBlock();

      // Check whether we can find a usable pg_ctl, and if not install one
      MutableList<String> findOrInstall = MutableList.<String>of()
              .append("which pg_ctl")
              .appendAll(Iterables.transform(pgctlLocations, StringFunctions.formatter("test -x %s/pg_ctl")))
              .append(installPackage(ImmutableMap.of(
                      "yum", "postgresql"+shortVersion+" postgresql"+shortVersion+"-server",
                      "apt", "postgresql-"+majorMinorVersion,
                      "port", "postgresql"+shortVersion+" postgresql"+shortVersion+"-server"
              ), null))
                      // due to impl of installPackage, it will not come to the line below I don't think
              .append(warn(format("WARNING: failed to find or install postgresql %s binaries", majorMinorVersion)));

      // Link to correct binaries folder (different versions of pg_ctl and psql don't always play well together)
      MutableList<String> linkFromHere = MutableList.<String>of()
              .append(ifExecutableElse1("pg_ctl", chainGroup(
                      "PG_EXECUTABLE=`which pg_ctl`",
                      "PG_DIR=`dirname $PG_EXECUTABLE`",
                      "echo 'found pg_ctl in '$PG_DIR' on path so linking PG bin/ to that dir'",
                      "ln -s $PG_DIR bin")))
              .appendAll(Iterables.transform(pgctlLocations, givenDirIfFileExistsInItLinkToDir("pg_ctl", "bin")))
              .append(fail(format("WARNING: failed to find postgresql %s binaries for pg_ctl; aborting", majorMinorVersion), 9));

      newScript(INSTALLING)
              .body.append(
              dontRequireTtyForSudo(),
              ifExecutableElse0("yum", getYumRepository(version, majorMinorVersion, shortVersion)),
              ifExecutableElse0("apt-get", getAptRepository()),
              "rm -f bin", // if left over from previous incomplete/failed install (not sure why that keeps happening!)
              alternativesGroup(findOrInstall),
              alternativesGroup(linkFromHere))
              .failOnNonZeroResultCode()
              .queue();
   }

   private String getYumRepository(String version, String majorMinorVersion, String shortVersion) {
      return
              chainGroup(
                      sudo(format("wget http://yum.postgresql.org/%s/redhat/rhel-%s-%s/pgdg-%s%s-%s.noarch.rpm", majorMinorVersion, getMachine().getOsDetails().getVersion(), getMachine().getOsDetails().getArch(), getOsNme(), shortVersion, version)),
                      sudo(format("rpm -Uvh pgdg-%s%s-%s.noarch.rpm", getOsNme(), shortVersion, version))
              );
   }

   private String getAptRepository() {
      return "wget --quiet -O - http://apt.postgresql.org/pub/repos/apt/ACCC4CF8.asc | sudo tee -a apt-key add -;" +
              "echo \"deb http://apt.postgresql.org/pub/repos/apt/   $(sudo lsb_release --codename --short)-pgdg main\" | sudo tee -a /etc/apt/sources.list.d/postgresql.list";
   }

   private String getOsNme() {
      if(getMachine().getOsDetails().getName().equals("rhel"))
         return "redhat";
      return getMachine().getOsDetails().getName();
   }

   private static Function<String, String> givenDirIfFileExistsInItLinkToDir(final String filename, final String linkToMake) {
      return new Function<String, String>() {
         public String apply(@Nullable String dir) {
            return ifExecutableElse1(Urls.mergePaths(dir, filename),
                    chainGroup("echo 'found "+filename+" in "+dir+" so linking to it in "+linkToMake+"'", "ln -s "+dir+" "+linkToMake));
         }
      };
   }

   @Override
   public void customize() {
      // Some OSes start postgres during package installation
      DynamicTasks.queue(SshEffectorTasks.ssh(sudoAsUser("postgres", "/etc/init.d/postgresql stop")).allowingNonZeroExitCode()).get();

      newScript(CUSTOMIZING)
              .body.append(
              sudo("mkdir -p " + getDataDir()),
              sudo("chown postgres:postgres " + getDataDir()),
              sudo("chmod 700 " + getDataDir()),
              sudo("touch " + getLogFile()),
              sudo("chown postgres:postgres " + getLogFile()),
              sudo("touch " + getPidFile()),
              sudo("chown postgres:postgres " + getPidFile()),
              alternativesGroup(
                      chainGroup(format("test -e %s", getInstallDir() + "/bin/initdb"),
                              sudoAsUser("postgres", getInstallDir() + "/bin/initdb -D " + getDataDir())),
                      callPgctl("initdb", true)))
              .failOnNonZeroResultCode()
              .execute();

      String configUrl = getEntity().getConfig(PostgreSqlNode.CONFIGURATION_FILE_URL);
      if (Strings.isBlank(configUrl)) {
         // http://wiki.postgresql.org/wiki/Tuning_Your_PostgreSQL_Server
         // If the same setting is listed multiple times, the last one wins.
         DynamicTasks.queue(SshEffectorTasks.ssh(
                 executeCommandThenAsUserTeeOutputToFile(
                         chainGroup(
                                 "echo \"listen_addresses = '*'\"",
                                 "echo \"port = " + getEntity().getPostgreSqlPort() +  "\"",
                                 "echo \"max_connections = " + getEntity().getMaxConnections() +  "\"",
                                 "echo \"shared_buffers = " + getEntity().getSharedMemory() +  "\"",
                                 "echo \"external_pid_file = '" + getPidFile() +  "'\""),
                         "postgres", getDataDir() + "/postgresql.conf")));
      } else {
         String contents = processTemplate(configUrl);
         DynamicTasks.queue(
                 SshEffectorTasks.put("/tmp/postgresql.conf").contents(contents),
                 SshEffectorTasks.ssh(sudoAsUser("postgres", "cp /tmp/postgresql.conf " + getDataDir() + "/postgresql.conf")));
      }

      String authConfigUrl = getEntity().getConfig(PostgreSqlNode.AUTHENTICATION_CONFIGURATION_FILE_URL);
      if (Strings.isBlank(authConfigUrl)) {
         DynamicTasks.queue(SshEffectorTasks.ssh(
                 // TODO give users control which hosts can connect and the authentication mechanism
                 executeCommandThenAsUserTeeOutputToFile("echo \"host all all 0.0.0.0/0 md5\"", "postgres", getDataDir() + "/pg_hba.conf")));
      } else {
         String contents = processTemplate(authConfigUrl);
         DynamicTasks.queue(
                 SshEffectorTasks.put("/tmp/pg_hba.conf").contents(contents),
                 SshEffectorTasks.ssh(sudoAsUser("postgres", "cp /tmp/pg_hba.conf " + getDataDir() + "/pg_hba.conf")));
      }

      // Wait for commands to complete before running the creation script
      DynamicTasks.waitForLast();

      // Capture log file contents if there is an error configuring the database
      try {
         executeDatabaseCreationScript();
      } catch (RuntimeException r) {
         copyLogFileContents();
         throw Exceptions.propagate(r);
      }

      // Try establishing an external connection. If you get a "Connection refused...accepting TCP/IP connections
      // on port 5432?" error then the port is probably closed. Check that the firewall allows external TCP/IP
      // connections (netstat -nap). You can open a port with lokkit or by configuring the iptables.
   }

   protected void executeDatabaseCreationScript() {
      if (copyDatabaseCreationScript()) {
         newScript("running postgres creation script")
                 .body.append(
                 "cd " + getInstallDir(),
                 callPgctl("start", true),
                 sudoAsUser("postgres", getInstallDir() + "/bin/psql -p " + entity.getAttribute(PostgreSqlNode.POSTGRESQL_PORT) + " --file " + getRunDir() + "/creation-script.sql"),
                 callPgctl("stop", true))
                 .failOnNonZeroResultCode()
                 .execute();
      }
   }

   private boolean copyDatabaseCreationScript() {
      InputStream creationScript = DatastoreMixins.getDatabaseCreationScript(entity);
      if (creationScript==null)
         return false;
      getMachine().copyTo(creationScript, getRunDir() + "/creation-script.sql");
      return true;
   }

   public String getDataDir() {
      return getRunDir() + "/data";
   }

   public String getLogFile() {
      return getRunDir() + "/postgresql.log";
   }

   public String getPidFile() {
      return getRunDir() + "/postgresql.pid";
   }

   public void copyLogFileContents() {
      try {
         File file = File.createTempFile("postgresql-log", getEntity().getId());
         int result = getMachine().copyFrom(getLogFile(), file.getAbsolutePath());
         if (result != 0) throw new IllegalStateException("Could not access log file " + getLogFile());
         log.info("Saving {} contents as {}", getLogFile(), file);
         Streams.logStreamTail(log, "postgresql.log", Streams.byteArrayOfString(Files.toString(file, Charsets.UTF_8)), 1024);
      } catch (IOException ioe) {
         log.debug("Error reading copied log file: {}", ioe);
      }
   }

   protected String callPgctl(String command, boolean waitForIt) {
      return sudoAsUser("postgres", getInstallDir() + "/bin/pg_ctl -D " + getDataDir() +
              " -l " + getLogFile() + (waitForIt ? " -w " : " ") + command);
   }

   @Override
   public void launch() {
      log.info(String.format("Starting entity %s at %s", this, getLocation()));
      newScript(MutableMap.of("usePidFile", false), LAUNCHING)
              .body.append(callPgctl("start", false))
              .execute();
   }

   @Override
   public boolean isRunning() {
      return newScript(MutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING)
              .body.append(getStatusCmd())
              .execute() == 0;
   }

   @Override
   public void stop() {
      newScript(MutableMap.of("usePidFile", false), STOPPING)
              .body.append(callPgctl((entity.getConfig(PostgreSqlNode.DISCONNECT_ON_STOP) ? "-m immediate " : "") + "stop", false))
              .failOnNonZeroResultCode()
              .execute();
      newScript(MutableMap.of("usePidFile", getPidFile(), "processOwner", "postgres"), STOPPING).execute();
   }

   @Override
   public PostgreSqlNodeImpl getEntity() {
      return (PostgreSqlNodeImpl) super.getEntity();
   }

   @Override
   public String getStatusCmd() {
      return callPgctl("status", false);
   }

   public ProcessTaskWrapper<Integer> executeScriptAsync(String commands) {
      String filename = "postgresql-commands-"+Identifiers.makeRandomId(8);
      DynamicTasks.queue(SshEffectorTasks.put(Urls.mergePaths(getRunDir(), filename)).contents(commands).summary("copying datastore script to execute "+filename));
      return executeScriptFromInstalledFileAsync(filename);
   }

   public ProcessTaskWrapper<Integer> executeScriptFromInstalledFileAsync(String filenameAlreadyInstalledAtServer) {
      return DynamicTasks.queue(
              SshEffectorTasks.ssh(
                      "cd "+getRunDir(),
                      sudoAsUser("postgres", getInstallDir() + "/bin/psql -p " + entity.getAttribute(PostgreSqlNode.POSTGRESQL_PORT) + " --file " + filenameAlreadyInstalledAtServer))
                      .summary("executing datastore script "+filenameAlreadyInstalledAtServer));
   }

}
