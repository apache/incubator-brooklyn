package brooklyn.entity.database.postgresql;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.chef.ChefLiveTestSupport;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/** 
 * Tests Chef installation of PostgreSql. Requires chef-server (knife).
 * <p> 
 * To be able to run repeatedly on the same box, you will need the patched version of the postgresql library,
 * at https://github.com/opscode-cookbooks/postgresql/pull/73 .
 *  
 * @author alex
 *
 */
public class PostgreSqlChefTest extends ChefLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlChefTest.class);
    
    PostgreSqlNode psql;
    
    @Test(groups="Live")
    public void testPostgresStartsAndStops() throws Exception {
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        psql = app.createAndManageChild(PostgreSqlSpecs.specChef());

        app.start(ImmutableList.of(targetLocation));
        
        Entities.submit(psql, SshEffectorTasks.ssh("ps aux | grep [p]ostgres").requiringExitCodeZero());
        SshMachineLocation targetMachine = EffectorTasks.getSshMachine(psql);
        
        psql.stop();
        
        try {
            // if host is still contactable ensure postgres is not running
            ProcessTaskWrapper<Integer> t = Entities.submit(app, SshEffectorTasks.ssh("ps aux | grep [p]ostgres").machine(targetMachine).allowingNonZeroExitCode());
            t.getTask().blockUntilEnded(Duration.TEN_SECONDS);
            if (!t.isDone())
                Assert.fail("Task not finished yet: "+t.getTask());
            Assert.assertNotEquals(t.get(), (Integer)0, "Task ended with code "+t.get()+"; output: "+t.getStdout() );
        } catch (Exception e) {
            // host has been killed, that is fine
            log.info("Machine "+targetMachine+" destroyed on stop (expected - "+e+")");
        }
    }
    
    @Test(groups="Live")
    public void testPostgresScriptAndAccess() throws Exception {
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        PortRange randomPort = PortRanges.fromString(""+(5420+new Random().nextInt(10))+"+");
        psql = app.createAndManageChild(PostgreSqlSpecs.specChef()
                .configure(PostgreSqlNode.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, randomPort)
            );

        app.start(ImmutableList.of(targetLocation));

        String url = psql.getAttribute(PostgreSqlNode.DB_URL);
        log.info("Trying to connect to "+psql+" at "+url);
        Assert.assertNotNull(url);
        Assert.assertTrue(url.contains("542"));
        
        new VogellaExampleAccess("org.postgresql.Driver", url).readModifyAndRevertDataBase();
    }

}

