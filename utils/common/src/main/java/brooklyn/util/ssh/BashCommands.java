package brooklyn.util.ssh;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

public class BashCommands {

    /**
     * Returns a string for checking whether the given executable is available,
     * and installing it if necessary.
     * <p/>
     * Uses {@link #installPackage} and accepts the same flags e.g. for apt, yum, rpm.
     */
    public static String installExecutable(Map<?,?> flags, String executable) {
        return onlyIfExecutableMissing(executable, installPackage(flags, executable));
    }

    public static String installExecutable(String executable) {
        return installExecutable(MutableMap.of(), executable);
    }

    /**
     * Returns a command with all output redirected to /dev/null
     */
    public static String quiet(String command) {
        return format("(%s > /dev/null 2>&1)", command);
    }

    /**
     * Returns a command that always exits successfully
     */
    public static String ok(String command) {
        return String.format("(%s || true)", command);
    }

    /**
     * Returns a command for safely running as root, using {@code sudo}.
     * <p/>
     * Ensuring non-blocking if password not set by using 
     * {@code -n} which means to exit if password required,
     * and (perhaps unnecessarily ?)
     * {@code -S} which reads from stdin (routed to {@code /dev/null}, it was claimed here previously, though I'm not sure?).
     * <p/>
     * Also specify {@code -E} to pass the parent environment in.
     * <p/> 
     * If already root, simply runs the command, wrapped in brackets in case it is backgrounded.
     * <p/>
     * The command is not quoted or escaped in any ways. 
     * If you are doing privileged redirect you may need to pass e.g. "bash -c 'echo hi > file'".
     * <p/>
     * If null is supplied, it is returned (sometimes used to indicate no command desired).
     */
    public static String sudo(String command) {
        if (command==null) return null;
        return format("( if test \"$UID\" -eq 0; then ( %s ); else sudo -E -n -S -- %s; fi )", command, command);
    }

    /** sudo to a given user and run the indicated command*/
    public static String sudoAsUser(String user, String command) {
        if (command == null) return null;
        return format("{ sudo -E -n -u %s -s -- %s ; }", user, command);
    }

    /** executes a command, then as user tees the output to the given file. 
     * useful e.g. for appending to a file which is only writable by root or a priveleged user. */
    public static String executeCommandThenAsUserTeeOutputToFile(String commandWhoseOutputToWrite, String user, String file) {
        return format("{ %s | sudo -E -n -u %s -s -- tee -a %s ; }",
                commandWhoseOutputToWrite, user, file);
    }


    /** some machines require a tty for sudo; brooklyn by default does not use a tty
     * (so that it can get separate error+stdout streams); you can enable a tty as an
     * option to every ssh command, or you can do it once and 
     * modify the machine so that a tty is not subsequently required.
     * <p>
     * this command must be run with allocatePTY set as a flag to ssh.  see SshTasks.dontRequireTtyForSudo which sets that up. 
     * <p>
     * (having a tty for sudo seems like another case of imaginary security which is just irritating.
     * like water restrictions at airport security.) */
    public static String dontRequireTtyForSudo() {
        return ifFileExistsElse0("/etc/sudoers", sudo("sed -i.brooklyn.bak 's/.*requiretty.*/#brooklyn-removed-require-tty/' /etc/sudoers"));
    }

    /**
     * Returns a command that runs only if the operating system is as specified; Checks {@code /etc/issue} for the specified name
     * @deprecated since 0.6.0 very non-portable
     */
    public static String on(String osName, String command) {
        return format("( grep \"%s\" /etc/issue && %s )", osName, command);
    }

    /**
     * Returns a command that runs only if the specified file exists
     * @deprecated since 0.6.0 use {@link #ifFileExistsElse0(String, String)} or {@link #ifFileExistsElse1(String, String)}   
     */
    public static String file(String path, String command) {
        return format("( test -f %s && %s )", path, command);
    }

    // TODO a builder would be better than these ifBlahExistsElseBlah methods!
    // (ideally formatting better also; though maybe SshTasks would be better?)
    
    /**
     * Returns a command that runs only if the specified file (or link or directory) exists;
     * if the command runs and fails that exit is preserved (but if the file does not exist exit code is zero).
     * Executed as  { { not-file-exists && ok ; } || command ; }  for portability.
     * ("if [ ... ] ; then xxx ; else xxx ; fi" syntax is not quite as portable, I seem to recall (not sure, Alex Aug 2013).) 
     */
    public static String ifFileExistsElse0(String path, String command) {
        return alternativesGroup(
                chainGroup(format("test ! -e %s", path), "true"),
                command);
    }
    /** as {@link #ifFileExistsElse0(String, String)} but returns non-zero if the test fails (also returns non-zero if the command fails,
     * so you can't tell the difference :( -- we need if ; then ; else ; fi semantics for that I think, but not sure how portable that is) */
    public static String ifFileExistsElse1(String path, String command) {
        return chainGroup(format("test -e %s", path), command);
    }

    /**
     * Returns a command that runs only if the specified executable is in the path.
     * If command is null, no command runs (and the script component this creates will return true if the executable).
     * @deprecated since 0.6.0 use {@link #ifExecutableElse0(String, String)}
     */
    public static String exists(String executable, String ...commands) {
        String extraCommandsAnded = "";
        for (String c: commands) if (c!=null) extraCommandsAnded += " && "+c;
        return format("( which %s%s )", executable, extraCommandsAnded);
    }

    /**
     * Returns a command that runs only if the specified executable exists on the path (using `which`).
     * if the command runs and fails that exit is preserved (but if the executable is not on the path exit code is zero).
     * @see #ifFileExistsElse0(String, String) for implementation discussion, using <code>{ { test -z `which executable` && true ; } || command ; } 
     */
    public static String ifExecutableElse0(String executable, String command) {
        return alternativesGroup(
                chainGroup(format("test -z `which %s`", executable), "true"),
                command);
    }

    /** as {@link #ifExecutableElse0(String, String)} but returns 1 if the test fails (also returns non-zero if the command fails) */
    public static String ifExecutableElse1(String executable, String command) {
        return chainGroup(format("which %s", executable), command);
    }

    /**
     * Returns a command that runs only if the specified executable is NOT in the path
     * @deprecated since 0.6.0 use {@link #onlyIfExecutableMissing(String, String)}
     */
    public static String missing(String executable, String command) {
        return format("( which %s || %s )", executable, command);
    }

    /**
     * Returns a command that runs only if the specified executable exists on the path (using `which`).
     * if the command runs and fails that exit is preserved (but if the executable is not on the path exit code is zero).
     * @see #ifFileExistsElse0(String, String) for implementation discussion, using <code>{ { test -z `which executable` && true ; } || command ; } 
     */
    public static String onlyIfExecutableMissing(String executable, String command) {
        return alternativesGroup(format("which %s", executable), command);
    }
    
    /**
     * Returns a sequence of chained commands that runs until one of them fails (i.e. joined by '&&')
     * This currently runs as a subshell (so exits are swallowed) but behaviour may be changed imminently. 
     * (Use {@link #chainGroup(Collection)} or {@link #chainSubshell(Collection)} to be clear.)
     */
    public static String chain(Collection<String> commands) {
        return "( " + Strings.join(commands, " && ") + " )";
    }

    /** Convenience for {@link #chain(Collection)} */
    public static String chain(String ...commands) {
        return "( " + Strings.join(commands, " && ") + " )";
    }

    /** As {@link #chain(Collection)}, but explicitly using { } grouping characters
     * to ensure exits are propagated. */
    public static String chainGroup(Collection<String> commands) {
        // spaces required around curly braces
        return format("{ " + Strings.join(commands, " && ") + " ; }");
    }

    /** As {@link #chainGroup(Collection)} */
    public static String chainGroup(String ...commands) {
        return format("{ " + Strings.join(commands, " && ") + " ; }");
    }

    /** As {@link #chain(Collection)}, but explicitly using ( ) grouping characters
     * to ensure exits are caught. */
    public static String chainSubshell(Collection<String> commands) {
        // the spaces are not required, but it might be possible that a (( expr )) is interpreted differently
        // (won't hurt to have the spaces in any case!) 
        return format("( " + Strings.join(commands, " && ") + " )");
    }

    /** As {@link #chainSubshell(Collection)} */
    public static String chainSubshell(String ...commands) {
        return format("( " + Strings.join(commands, " && ") + "  )");
    }

    /**
     * Returns a sequence of alternative commands that runs until one of the commands succeeds;
     * or if none succeed, it will run the failure command.
     * @deprecated since 0.6.0; this method just treats the failure command as another alternative so hardly seems worth it; 
     * prefer {@link #alternatives(Collection)} or  
     */ @Deprecated
    public static String alternatives(Collection<String> commands, String failureCommand) {
        return format("(%s || %s)", Strings.join(commands, " || "), failureCommand);
    }
    
    /**
     * Returns a sequence of chained commands that runs until one of them succeeds (i.e. joined by '||').
     * This currently runs as a subshell (so exits are swallowed) but behaviour may be changed imminently. 
     * (Use {@link #alternativesGroup(Collection)} or {@link #alternativesSubshell(Collection)} to be clear.)
     */
    public static String alternatives(Collection<String> commands) {
        return format("( " + Strings.join(commands, " || ") + " )");
    }

    /** As {@link #alternatives(Collection)} */
    public static String alternatives(String ...commands) {
        return format("( " + Strings.join(commands, " || ") + " )");
    }

    /** As {@link #alternatives(Collection)}, but explicitly using { } grouping characters
     * to ensure exits are propagated. */
    public static String alternativesGroup(Collection<String> commands) {
        // spaces required around curly braces
        return format("{ " + Strings.join(commands, " || ") + " ; }");
    }

    /** As {@link #alternativesGroup(Collection)} */
    public static String alternativesGroup(String ...commands) {
        return format("{ " + Strings.join(commands, " || ") + " ; }");
    }

    /** As {@link #alternatives(Collection)}, but explicitly using ( ) grouping characters
     * to ensure exits are caught. */
    public static String alternativesSubshell(Collection<String> commands) {
        // the spaces are not required, but it might be possible that a (( expr )) is interpreted differently
        // (won't hurt to have the spaces in any case!) 
        return format("( " + Strings.join(commands, " || ") + " )");
    }

    /** As {@link #alternativesSubshell(Collection)} */
    public static String alternativesSubshell(String ...commands) {
        return format("( " + Strings.join(commands, " || ") + "  )");
    }

    /** returns the pattern formatted with the given arg if the arg is not null, otherwise returns null */
    public static String formatIfNotNull(String pattern, Object arg) {
        if (arg==null) return null;
        return format(pattern, arg);
    }
    
    /**
     * Returns a command for installing the given package.
     * Warns, but does not fail or return non-zero if it ultimately fails.
     * <p/>
     * Flags can contain common overrides for deb, apt, yum, rpm and port
     * as the package names can be different for each of those:
     * <pre>
     * installPackage("libssl-devel", yum:"openssl-devel", apt:"openssl libssl-dev zlib1g-dev")
     * </pre>
     */
    // TODO other variants, which fail if it can't install, or return non-zero (so callers can wrap in "ok" if it's not required)
    public static String installPackage(Map<?,?> flags, String packageDefaultName) {
        String ifmissing = (String) flags.get("onlyifmissing");
        
        String aptInstall = formatIfNotNull("apt-get install -y --allow-unauthenticated %s", getFlag(flags, "apt", packageDefaultName));
        String yumInstall = formatIfNotNull("yum -y --nogpgcheck install %s", getFlag(flags, "yum", packageDefaultName));
        String brewInstall = formatIfNotNull("brew install %s", getFlag(flags, "brew", packageDefaultName));
        String portInstall = formatIfNotNull("port install %s", getFlag(flags, "port", packageDefaultName));
        
        List<String> commands = new LinkedList<String>();
        if (ifmissing != null) commands.add(format("which %s", ifmissing));
        commands.add(ifExecutableElse1("apt-get",
                chainGroup(
                "echo apt-get exists, doing update",
                "export DEBIAN_FRONTEND=noninteractive",
                sudo("apt-get update"), 
                sudo(aptInstall))));
        commands.add(ifExecutableElse1("yum", sudo(yumInstall)));
        commands.add(ifExecutableElse1("brew", brewInstall));
        commands.add(ifExecutableElse1("port", sudo(portInstall)));
        
        commands.add(warn("WARNING: no known/successful package manager to install " +
                (packageDefaultName!=null ? packageDefaultName : flags.toString()) +
                ", may fail subsequently"));
        return alternatives(commands);
    }
    
    public static String warn(String message) {
        return "( echo "+BashStringEscapes.wrapBash(message)+" | tee /dev/stderr )";
    }

    /** returns a command which logs a message to stdout and stderr then exits with the given error code */
    public static String fail(String message, int code) {
        return chainGroup(warn(message), "exit "+code);
    }

    /** requires the command to have a non-zero exit code; e.g.
     * <code>require("which foo", "Command foo must be found", 1)</code> */
    public static String require(String command, String failureMessage, int exitCode) {
        return alternativesGroup(command, fail(failureMessage, exitCode));
    }

    /** as {@link #require(String, String, int)} but returning the original exit code */
    public static String require(String command, String failureMessage) {
        return alternativesGroup(command, chainGroup("EXIT_CODE=$?", warn(failureMessage), "exit $EXIT_CODE"));
    }

    /** requires the test to pass, as valid bash `test` arguments; e.g.
     * <code>requireTest("-f /etc/hosts", "Hosts file must exist", 1)</code> */
    public static String requireTest(String test, String failureMessage, int exitCode) {
        return require("test "+test, failureMessage, exitCode);
    }

    /** as {@link #requireTest(String, String, int)} but returning the original exit code */
    public static String requireTest(String test, String failureMessage) {
        return require("test "+test, failureMessage);
    }

    /** fails with nice error if the given file does not exist */
    public static String requireFile(String file) {
        return requireTest("-f "+BashStringEscapes.wrapBash(file), "The required file \""+file+"\" does not exist");
    }

    /** fails with nice error if the given file does not exist */
    public static String requireExecutable(String command) {
        return require("which "+BashStringEscapes.wrapBash(command), "The required executable \""+command+"\" does not exist");
    }

    public static String installPackage(String packageDefaultName) {
        return installPackage(MutableMap.of(), packageDefaultName);
    }

    public static final String INSTALL_TAR = installExecutable("tar");
    public static final String INSTALL_CURL = installExecutable("curl");
    public static final String INSTALL_WGET = installExecutable("wget");
    public static final String INSTALL_ZIP = installExecutable("zip");
    public static final String INSTALL_UNZIP = alternatives(installExecutable("unzip"), installExecutable("zip"));

    /** 
     * @see downloadUrlAs(Map, String, String, String)
     * @deprecated since 0.5.0 - Use {@link downloadUrlAs(List<String>, String)}, and rely on {@link DownloadResolverManager} to include the local-repo
     */
    @Deprecated
    public static List<String> downloadUrlAs(String url, String entityVersionPath, String pathlessFilenameToSaveAs) {
        return downloadUrlAs(MutableMap.of(), url, entityVersionPath, pathlessFilenameToSaveAs);
    }

    /**
    /**
     * Returns command for downloading from a url and saving to a file;
     * currently using {@code curl}.
     * <p/>
     * Will read from a local repository, if files have been copied there
     * ({@code cp -r /tmp/brooklyn/installs/ ~/.brooklyn/repository/<entityVersionPath>/<pathlessFilenameToSaveAs>})
     * unless <em>skipLocalRepo</em> is {@literal true}.
     * <p/>
     * Ideally use a blobstore staging area.
     * 
     * @deprecated since 0.5.0 - Use {@link downloadUrlAs(List, String)}, and rely on {@link DownloadResolverManager} to include the local-repo
     */
    @Deprecated
    public static List<String> downloadUrlAs(Map<?,?> flags, String url, String entityVersionPath, String pathlessFilenameToSaveAs) {
        Boolean skipLocalRepo = (Boolean) flags.get("skipLocalRepo");
        boolean useLocalRepo =  skipLocalRepo!=null?!skipLocalRepo:true;
        List<String> urls;
        if (useLocalRepo) {
            String localRepoFileUrl = format("file://$HOME/.brooklyn/repository/%s/%s", entityVersionPath, pathlessFilenameToSaveAs);
            urls = ImmutableList.of(localRepoFileUrl, url);
        } else {
            urls = ImmutableList.of(url);
        }
        return downloadUrlAs(urls, "./"+pathlessFilenameToSaveAs);
    }

    /** @deprecated since 0.6.0 use {@link #commandsToDownloadUrlsAs(List, String)} (because method uses a list),
     * or {@link #commandToDownloadUrlsAs(List, String)} for the single-string variant */ @Deprecated
    public static List<String> downloadUrlAs(List<String> urls, String saveAs) {
        return Arrays.asList(INSTALL_CURL, 
                require(simpleDownloadUrlAs(urls, saveAs), "Could not retrieve "+saveAs+" (from "+urls.size()+" sites)", 9));
    }
    /**
     * Returns commands to download the URL, saving as the given file. Will try each URL in turn until one is successful
     * (see `curl -f` documentation).
     */
    public static List<String> commandsToDownloadUrlsAs(List<String> urls, String saveAs) {
        return Arrays.asList(INSTALL_CURL, 
                require(simpleDownloadUrlAs(urls, saveAs), "Could not retrieve "+saveAs+" (from "+urls.size()+" sites)", 9));
    }
    public static String commandToDownloadUrlsAs(List<String> urls, String saveAs) {
        return chain(INSTALL_CURL, 
                require(simpleDownloadUrlAs(urls, saveAs), "Could not retrieve "+saveAs+" (from "+urls.size()+" sites)", 9));
    }
    public static String commandToDownloadUrlAs(String url, String saveAs) {
        return chain(INSTALL_CURL, 
                require(simpleDownloadUrlAs(Arrays.asList(url), saveAs), "Could not retrieve "+saveAs+" (from 1 site)", 9));
    }

    /**
     * Returns command to download the URL, sending the output to stdout --
     * suitable for redirect by appending " | tar xvf".
     * Will try each URL in turn until one is successful
     */
    public static String downloadToStdout(List<String> urls) {
        return chain(
                INSTALL_CURL + " > /dev/null", 
                require(simpleDownloadUrlAs(urls, null), 
                        "Could not retrieve file (from "+urls.size()+" sites)", 9));
    }
    
    /** as {@link #downloadToStdout(List)} but varargs for convenience */
    public static String downloadToStdout(String ...urls) {
        return downloadToStdout(Arrays.asList(urls));
    }

    /**
     * Same as {@link downloadUrlAs(List, String)}, except does not install curl, and does not exit on failure,
     * and if saveAs is null it downloads it so stdout.
     */
    public static String simpleDownloadUrlAs(List<String> urls, String saveAs) {
        if (urls.isEmpty()) throw new IllegalArgumentException("No URLs supplied to download "+saveAs);
        
        List<String> commands = new ArrayList<String>();
        for (String url : urls) {
            String command = format("curl -f -L \"%s\"", url);
            if (saveAs!=null)
                command = command + format(" -o %s", saveAs);
            commands.add(command);
        }
        return alternatives(commands);
    }

    private static Object getFlag(Map<?,?> flags, String flagName, Object defaultValue) {
        Object found = flags.get(flagName);
        return found == null ? defaultValue : found;
    }

    /**
     * Returns the command that installs Java 1.6.
     * See also: JavaSoftwareProcessSshDriver.installJava, which does a much more thorough job.
     *
     * @return the command that install Java 1.6.
     */
    public static String installJava6() {
        return installPackage(MutableMap.of("apt", "openjdk-6-jdk","yum", "java-1.6.0-openjdk-devel"), null);
    }

}
