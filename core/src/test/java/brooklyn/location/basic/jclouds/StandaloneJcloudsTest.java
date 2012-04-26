package brooklyn.location.basic.jclouds;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.jclouds.Constants;
import org.jclouds.aws.ec2.reference.AWSEC2Constants;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;

public class StandaloneJcloudsTest {

    public static final Logger LOG = LoggerFactory.getLogger(StandaloneJcloudsTest.class);
    
    static BrooklynProperties globals = BrooklynProperties.Factory.newDefault();

    String identity = globals.getFirst("brooklyn.jclouds.aws-ec2.identity");
    String credential = globals.getFirst("brooklyn.jclouds.aws-ec2.credential");
    
    @Test(groups={"WIP","Integration"})
    public void createVm() {
        String groupId = "mygroup-"+System.getProperty("user.name")+"-"+UUID.randomUUID().toString();
 
        Properties properties = new Properties();
        properties.setProperty(Constants.PROPERTY_PROVIDER, "aws-ec2");
        properties.setProperty(Constants.PROPERTY_IDENTITY, identity);
        properties.setProperty(Constants.PROPERTY_CREDENTIAL, credential);
        properties.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, Boolean.toString(true));
        properties.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, Boolean.toString(true));
        
        properties.setProperty(AWSEC2Constants.PROPERTY_EC2_AMI_QUERY, "state=available;image-type=machine");

        Iterable<Module> modules = ImmutableSet.<Module> of(new SshjSshClientModule(), new SLF4JLoggingModule());
        
        ComputeServiceContextFactory computeServiceFactory = new ComputeServiceContextFactory();
        
        final ComputeService computeService = computeServiceFactory
                .createContext("aws-ec2", modules, properties)
                .getComputeService();
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM");

            TemplateBuilder templateBuilder = computeService.templateBuilder();
            templateBuilder.locationId("eu-west-1");
            
            Template template = templateBuilder.build();
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            if (node == null) throw new IllegalStateException("No nodes returned");

            Credentials nodeCredentials = node.getCredentials();
            final LoginCredentials expectedCredentials = LoginCredentials.fromCredentials(nodeCredentials);
            
            LOG.info("Started VM, waiting for it to be sshable");
            boolean reachable = false;
            for (int i=0; i<120; i++) {
                try {
                    Statement statement = Statements.newStatementList(Statements.exec("date"));
                    ExecResponse response = computeService.runScriptOnNode(node.getId(), statement,
                            RunScriptOptions.Builder.overrideLoginCredentials(expectedCredentials));
                    if (response.getExitStatus() == 0) {
                        LOG.info("ssh 'date' succeeded");
                        reachable = true;
                        break;
                    }
                    LOG.info("ssh 'date' failed, exit "+response.getExitStatus()+", but still in retry loop");
                } catch (Exception e) {
                    if (i<120)
                        LOG.info("ssh 'date' failed, but still in retry loop: "+e);
                    else {
                        LOG.error("ssh 'date' failed after timeout: "+e, e);
                        Throwables.propagate(e);
                    }
                }
                Thread.sleep(1000);
            }
        
            if (!reachable) {
                throw new IllegalStateException("SSH failed, never reachable");
            }

        } catch (RunNodesException e) {
            if (e.getNodeErrors().size() > 0) {
                node = Iterables.get(e.getNodeErrors().keySet(), 0);
            }
            LOG.error("Failed to start VM: "+e, e);
            throw Throwables.propagate(e);
        } catch (Exception e) {
            LOG.error("Failed to start VM: "+e, e);
            throw Throwables.propagate(e);
        } finally {
            LOG.info("Now destroying VM: "+node);
            computeService.destroyNode( node.getId() );

            computeService.getContext().close();
        }
        
    }
}
