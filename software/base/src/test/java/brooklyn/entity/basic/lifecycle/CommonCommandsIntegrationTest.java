package brooklyn.entity.basic.lifecycle;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SshTasks;
import brooklyn.entity.basic.SshTasks.SshTask;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

public class CommonCommandsIntegrationTest {

    private ManagementContext mgmt;
    private BasicExecutionContext exec;
    
    private File destFile;
    private File sourceNonExistantFile = new File("/this/does/not/exist");
    private File sourceFile1;
    private File sourceFile2;
    private String sourceNonExistantFileUrl;
    private String sourceFileUrl1;
    private String sourceFileUrl2;
    private SshMachineLocation loc;

    private String localRepoFilename = "localrepofile.txt";
    private File localRepoBasePath;
    private File localRepoEntityBasePath;
    private String localRepoEntityVersionPath;
    private File localRepoEntityFile;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = Entities.newManagementContext();
        exec = new BasicExecutionContext(mgmt.getExecutionManager());
        
        destFile = java.io.File.createTempFile("commoncommands-test-dest", "txt");
        
        sourceNonExistantFile = new File("/this/does/not/exist");
        sourceNonExistantFileUrl = sourceNonExistantFile.toURI().toString();
        
        sourceFile1 = java.io.File.createTempFile("commoncommands-test", "txt");
        sourceFileUrl1 = sourceFile1.toURI().toString();
        Files.write("mysource1".getBytes(), sourceFile1);
        
        sourceFile2 = java.io.File.createTempFile("commoncommands-test2", "txt");
        sourceFileUrl2 = sourceFile2.toURI().toString();
        Files.write("mysource2".getBytes(), sourceFile2);

        localRepoEntityVersionPath = "commondcommands-test-dest-"+Identifiers.makeRandomId(8);
        localRepoBasePath = new File(format("%s/.brooklyn/repository", System.getProperty("user.home")));
        localRepoEntityBasePath = new File(localRepoBasePath, localRepoEntityVersionPath);
        localRepoEntityFile = new File(localRepoEntityBasePath, localRepoFilename);
        localRepoEntityBasePath.mkdirs();
        Files.write("mylocal1".getBytes(), localRepoEntityFile);

        loc = mgmt.getLocationManager().createLocation(LocalhostMachineProvisioningLocation.spec()).obtain();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (sourceFile1 != null) sourceFile1.delete();
        if (sourceFile2 != null) sourceFile2.delete();
        if (destFile != null) destFile.delete();
        if (localRepoEntityFile != null) localRepoEntityFile.delete();
        if (localRepoEntityBasePath != null) localRepoBasePath.delete();
        if (loc != null) loc.close();
        if (mgmt != null) Entities.destroyAll(mgmt);
    }
    
    @Test(groups="Integration")
    public void testSudo() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        String cmd = CommonCommands.sudo("whoami");
        int exitcode = loc.execCommands(ImmutableMap.of("out", outStream, "err", errStream), "test", ImmutableList.of(cmd));
        String outstr = new String(outStream.toByteArray());
        String errstr = new String(errStream.toByteArray());
        
        assertEquals(exitcode, 0, "out="+outstr+"; err="+errstr);
        assertTrue(outstr.contains("root"), "out="+outstr+"; err="+errstr);
    }
    
    public void testDownloadUrl() throws Exception {
        List<String> cmds = CommonCommands.downloadUrlAs(
                ImmutableList.of(sourceFileUrl1), 
                destFile.getAbsolutePath());
        int exitcode = loc.execCommands("test", cmds);
        
        assertEquals(0, exitcode);
        assertEquals(Files.readLines(destFile, Charsets.UTF_8), ImmutableList.of("mysource1"));
    }
    
    @Test(groups="Integration")
    public void testDownloadFirstSuccessfulFile() throws Exception {
        List<String> cmds = CommonCommands.downloadUrlAs(
                ImmutableList.of(sourceNonExistantFileUrl, sourceFileUrl1, sourceFileUrl2), 
                destFile.getAbsolutePath());
        int exitcode = loc.execCommands("test", cmds);
        
        assertEquals(0, exitcode);
        assertEquals(Files.readLines(destFile, Charsets.UTF_8), ImmutableList.of("mysource1"));
    }
    
    @Test(groups="Integration")
    public void testDownloadEntityUrlUsesLocalRepo() throws Exception {
        File destEntityFile = new File(destFile.getParentFile(), localRepoFilename);
        try {
            List<String> cmds = ImmutableList.<String>builder()
                    .add("cd "+destEntityFile.getParentFile().getAbsolutePath())
                    .addAll(CommonCommands.downloadUrlAs(
                            ImmutableMap.of(),
                            sourceFileUrl1, 
                            localRepoEntityVersionPath,
                            localRepoFilename))
                    .build();
            int exitcode = loc.execCommands("test", cmds);
            
            assertEquals(0, exitcode);
            assertEquals(Files.readLines(destEntityFile, Charsets.UTF_8), ImmutableList.of("mylocal1"));
        } finally {
            destEntityFile.delete();
        }
    }
    
    @Test(groups="Integration")
    public void testDownloadEntityUrlSkipsLocalRepo() throws Exception {
        File destEntityFile = new File(destFile.getParentFile(), localRepoFilename);
        try {
            List<String> cmds = ImmutableList.<String>builder()
                    .add("cd "+destEntityFile.getParentFile().getAbsolutePath())
                    .addAll(CommonCommands.downloadUrlAs(
                            ImmutableMap.of("skipLocalRepo", true),
                            sourceFileUrl1, 
                            localRepoEntityVersionPath,
                            localRepoFilename))
                    .build();
            int exitcode = loc.execCommands("test", cmds);
            
            assertEquals(0, exitcode);
            assertEquals(Files.readLines(destEntityFile, Charsets.UTF_8), ImmutableList.of("mysource1"));
        } finally {
            destEntityFile.delete();
        }
    }
    
    @Test(groups="Integration")
    public void testDownloadEntityUrl() throws Exception {
        File destEntityFile = new File(destFile.getParentFile(), localRepoFilename);
        try {
            List<String> cmds = ImmutableList.<String>builder()
                    .add("cd "+destEntityFile.getParentFile().getAbsolutePath())
                    .addAll(CommonCommands.downloadUrlAs(
                            ImmutableMap.of(),
                            sourceFileUrl1, 
                            "localnotthere",
                            localRepoFilename))
                    .build();
            int exitcode = loc.execCommands("test", cmds);
            
            assertEquals(0, exitcode);
            assertEquals(Files.readLines(destEntityFile, Charsets.UTF_8), ImmutableList.of("mysource1"));
        } finally {
            destEntityFile.delete();
        }
    }
    
    @Test(groups="Integration")
    public void testDownloadEntityUrlWhenNoneSupplied() throws Exception {
        List<String> cmds = ImmutableList.<String>builder()
                .add("cd "+destFile.getParentFile().getAbsolutePath())
                .addAll(CommonCommands.downloadUrlAs(
                        ImmutableMap.of(),
                        "localnotthere",
                        sourceNonExistantFileUrl,
                        destFile.getName()))
                .build();
        int exitcode = loc.execCommands("test", cmds);
        
        assertNotEquals(exitcode, 0);
        assertEquals(Files.readLines(destFile, Charsets.UTF_8), ImmutableList.of());
    }
    
    @Test(groups="Integration")
    public void testDownloadToStdout() throws Exception {
        SshTask<String> t = SshTasks.newInstance(
                "cd "+destFile.getParentFile().getAbsolutePath(),
                CommonCommands.downloadToStdout(Arrays.asList(sourceFileUrl1))+" | sed s/my/your/")
            .machine(loc)
            .requiringZeroAndReturningStdout();

        String result = exec.submit(t).get();
        assertTrue(result.trim().equals("yoursource1"), "Wrong contents of stdout download: "+result);
    }

}
