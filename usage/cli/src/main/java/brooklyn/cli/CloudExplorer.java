/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.cli;

import static com.google.common.base.Preconditions.checkNotNull;
import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.ParseException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.options.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.cli.Main.BrooklynCommand;
import brooklyn.cli.Main.BrooklynCommandCollectingArgs;
import brooklyn.cli.Main.HelpCommand;
import brooklyn.cli.Main.InfoCommand;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsUtil;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.exceptions.UserFacingException;
import brooklyn.util.stream.Streams;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Convenience for listing Cloud Compute and BlobStore details.
 * <p>
 * For fuller functionality, consider instead the jclouds CLI or Ruby Fog CLI.
 * <p>
 * The advantage of this utility is that it piggie-backs off the {@code brooklyn.property} credentials,
 * so requires less additional credential configuration. It also gives brooklyn-specific information,
 * such as which image will be used by default in a given cloud.
 */
public class CloudExplorer {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // Error codes
    public static final int SUCCESS = 0;
    public static final int PARSE_ERROR = 1;
    public static final int EXECUTION_ERROR = 2;
    public static final int CONFIGURATION_ERROR = 3;

    public static void main(String... args) {
        new CloudExplorer().execCli(args);
    }

    public static abstract class JcloudsCommand extends BrooklynCommandCollectingArgs {
        @Option(name = { "--all-locations" }, title = "all locations",
                description = "All locations (i.e. all locations in brooklyn.properties for which there are credentials)")
        public boolean allLocations;
        
        @Option(name = { "-l", "--location" }, title = "location spec",
                description = "A location spec (e.g. referring to a named location in brooklyn.properties file)")
        public String location;

        @Option(name = { "-y", "--yes" }, title = "auto-confirm",
                description = "Automatically answer yes to any questions")
        public boolean autoconfirm = false;

        protected abstract void doCall(JcloudsLocation loc, String indent) throws Exception;
        
        @Override
        public Void call() throws Exception {
            LocalManagementContext mgmt = new LocalManagementContext();
            List<JcloudsLocation> locs = Lists.newArrayList();
            try {
                if (location != null && allLocations) {
                    throw new FatalConfigurationRuntimeException("Must not specify --location and --all-locations");
                } else if (location != null) {
                    JcloudsLocation loc = (JcloudsLocation) mgmt.getLocationRegistry().resolve(location);
                    locs.add(loc);
                } else if (allLocations) {
                    // Find all named locations that point at different target clouds
                    Map<String, LocationDefinition> definedLocations = mgmt.getLocationRegistry().getDefinedLocations();
                    for (LocationDefinition locationDef : definedLocations.values()) {
                        Location loc = mgmt.getLocationRegistry().resolve(locationDef);
                        if (loc instanceof JcloudsLocation) {
                            boolean found = false;
                            for (JcloudsLocation existing : locs) {
                                if (equalTargets(existing, (JcloudsLocation) loc)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                locs.add((JcloudsLocation) loc);
                            }
                        }
                    }
                } else {
                    throw new FatalConfigurationRuntimeException("Must specify one of --location or --all-locations");
                }
                
                for (JcloudsLocation loc : locs) {
                    stdout.println("Location {");
                    stdout.println("\tprovider: "+loc.getProvider());
                    stdout.println("\tdisplayName: "+loc.getDisplayName());
                    stdout.println("\tidentity: "+loc.getIdentity());
                    if (loc.getEndpoint() != null) stdout.println("\tendpoint: "+loc.getEndpoint());
                    if (loc.getRegion() != null) stdout.println("\tregion: "+loc.getRegion());

                    try {
                        doCall(loc, "\t");
                    } finally {
                        stdout.println("}");
                    }
                }
            } finally {
                mgmt.terminate();
            }
            return null;
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("location", location);
        }
        
        protected boolean equalTargets(JcloudsLocation loc1, JcloudsLocation loc2) {
            return Objects.equal(loc1.getProvider(), loc2.getProvider())
                    && Objects.equal(loc1.getIdentity(), loc2.getIdentity())
                    && Objects.equal(loc1.getEndpoint(), loc2.getEndpoint())
                    && Objects.equal(loc1.getRegion(), loc2.getRegion());
        }
        
        
        protected boolean confirm(String msg, String indent) throws Exception {
            if (autoconfirm) {
                stdout.println(indent+"Auto-confirmed: "+msg);
                return true;
            } else {
                stdout.println(indent+"Enter y/n. Are you sure you want to "+msg);
                int in = stdin.read();
                boolean confirmed = (Character.toLowerCase(in) == 'y');
                if (confirmed) {
                    stdout.println(indent+"Confirmed; will "+msg);
                } else {
                    stdout.println(indent+"Declined; will not "+msg);
                }
                return confirmed;
            }
        }
    }
    
    public static abstract class ComputeCommand extends JcloudsCommand {
        protected abstract void doCall(ComputeService computeService, String indent) throws Exception;
            
        @Override
        protected void doCall(JcloudsLocation loc, String indent) throws Exception {
            ComputeService computeService = loc.getComputeService();
            doCall(computeService, indent);
        }
    }

    @Command(name = "list-instances", description = "")
    public static class ComputeListInstancesCommand extends ComputeCommand {
        @Override
        protected void doCall(ComputeService computeService, String indent) throws Exception {
            failIfArguments();
            Set<? extends ComputeMetadata> instances = computeService.listNodes();
            stdout.println(indent+"Instances {");
            for (ComputeMetadata instance : instances) {
                stdout.println(indent+"\t"+instance);
            }
            stdout.println(indent+"}");
        }
    }

    @Command(name = "list-images", description = "")
    public static class ComputeListImagesCommand extends ComputeCommand {
        @Override
        protected void doCall(ComputeService computeService, String indent) throws Exception {
            failIfArguments();
            Set<? extends Image> images = computeService.listImages();
            stdout.println(indent+"Images {");
            for (Image image : images) {
                stdout.println(indent+"\t"+image);
            }
            stdout.println(indent+"}");
        }
    }
    
    @Command(name = "list-hardware-profiles", description = "")
    public static class ComputeListHardwareProfilesCommand extends ComputeCommand {
        @Override
        protected void doCall(ComputeService computeService, String indent) throws Exception {
            failIfArguments();
            Set<? extends Hardware> hardware = computeService.listHardwareProfiles();
            stdout.println(indent+"Hardware Profiles {");
            for (Hardware image : hardware) {
                stdout.println(indent+"\t"+image);
            }
            stdout.println(indent+"}");
        }
    }
    
    @Command(name = "get-image", description = "")
    public static class ComputeGetImageCommand extends ComputeCommand {
        @Override
        protected void doCall(ComputeService computeService, String indent) throws Exception {
            if (arguments.isEmpty()) {
                throw new ParseException("Requires at least one image-id arguments");
            }
            
            for (String imageId : arguments) {
                Image image = computeService.getImage(imageId);
                stdout.println(indent+"Image "+imageId+" {");
                stdout.println(indent+"\t"+image);
                stdout.println(indent+"}");
            }
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("imageIds", arguments);
        }
    }

    @Command(name = "default-template", description = "")
    public static class ComputeDefaultTemplateCommand extends JcloudsCommand {
        @Override
        protected void doCall(JcloudsLocation loc, String indent) throws Exception {
            failIfArguments();
            ComputeService computeService = loc.getComputeService();
            
            Template template = loc.buildTemplate(computeService, loc.getAllConfigBag());
            Image image = template.getImage();
            Hardware hardware = template.getHardware();
            org.jclouds.domain.Location location = template.getLocation();
            TemplateOptions options = template.getOptions();
            stdout.println(indent+"Default template {");
            stdout.println(indent+"\tImage: "+image);
            stdout.println(indent+"\tHardware: "+hardware);
            stdout.println(indent+"\tLocation: "+location);
            stdout.println(indent+"\tOptions: "+options);
            stdout.println(indent+"}");
        }
    }
    
    @Command(name = "terminate-instances", description = "")
    public static class ComputeTerminateInstancesCommand extends ComputeCommand {
        @Override
        protected void doCall(ComputeService computeService, String indent) throws Exception {
            if (arguments.isEmpty()) {
                throw new ParseException("Requires at least one instance-id arguments");
            }
            
            for (String instanceId : arguments) {
                NodeMetadata instance = computeService.getNodeMetadata(instanceId);
                if (instance == null) {
                    stderr.println(indent+"Cannot terminate instance; could not find "+instanceId);
                } else {
                    boolean confirmed = confirm(indent, "terminate "+instanceId+" ("+instance+")");
                    if (confirmed) {
                        computeService.destroyNode(instanceId);
                    }
                }
            }
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("instanceIds", arguments);
        }
    }

    public static abstract class BlobstoreCommand extends JcloudsCommand {
        protected abstract void doCall(BlobStore blobstore, String indent) throws Exception;

        @Override
        protected void doCall(JcloudsLocation loc, String indent) throws Exception {
            String identity = checkNotNull(loc.getConfig(LocationConfigKeys.ACCESS_IDENTITY), "identity must not be null");
            String credential = checkNotNull(loc.getConfig(LocationConfigKeys.ACCESS_CREDENTIAL), "credential must not be null");
            String provider = checkNotNull(loc.getConfig(LocationConfigKeys.CLOUD_PROVIDER), "provider must not be null");
            String endpoint = loc.getConfig(CloudLocationConfig.CLOUD_ENDPOINT);
            
            BlobStoreContext context = JcloudsUtil.newBlobstoreContext(provider, endpoint, identity, credential, true);
            try {
                BlobStore blobStore = context.getBlobStore();
                doCall(blobStore, indent);
            } finally {
                context.close();
            }
        }
    }
    
    @Command(name = "list-containers", description = "")
    public static class BlobstoreListContainersCommand extends BlobstoreCommand {
        @Override
        protected void doCall(BlobStore blobstore, String indent) throws Exception {
            failIfArguments();
            Set<? extends StorageMetadata> containers = blobstore.list();
            stdout.println(indent+"Containers {");
            for (StorageMetadata container : containers) {
                stdout.println(indent+"\t"+container);
            }
            stdout.println(indent+"}");
        }
    }

    @Command(name = "list-container", description = "")
    public static class BlobstoreListContainerCommand extends BlobstoreCommand {
        @Override
        protected void doCall(BlobStore blobStore, String indent) throws Exception {
            if (arguments.isEmpty()) {
                throw new ParseException("Requires at least one container-name arguments");
            }
            
            for (String containerName : arguments) {
                Set<? extends StorageMetadata> contents = blobStore.list(containerName);
                stdout.println(indent+"Container "+containerName+" {");
                for (StorageMetadata content : contents) {
                    stdout.println(indent+"\t"+content);
                }
                stdout.println(indent+"}");
            }
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("containers", arguments);
        }
    }
    
    @Command(name = "blob", description = "")
    public static class BlobstoreGetBlobCommand extends BlobstoreCommand {
        @Option(name = { "--container" }, title = "list contents of a given container",
                description = "")
        public String container;

        @Option(name = { "--blob" }, title = "retrieves the blog in the given container",
                description = "")
        public String blob;

        @Override
        protected void doCall(BlobStore blobStore, String indent) throws Exception {
            failIfArguments();
            Blob content = blobStore.getBlob(container, blob);
            stdout.println(indent+"Blob "+container+" : " +blob +" {");
            stdout.println(indent+"\tHeaders {");
            for (Map.Entry<String, String> entry : content.getAllHeaders().entries()) {
                stdout.println(indent+"\t\t"+entry.getKey() + " = " + entry.getValue());
            }
            stdout.println(indent+"\t}");
            stdout.println(indent+"\tmetadata : "+content.getMetadata());
            stdout.println(indent+"\tpayload : "+Streams.readFullyString(content.getPayload().openStream()));
            stdout.println(indent+"}");
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("container", container)
                    .add("blob", blob);
        }
    }

    /** method intended for overriding when the script filename is different 
     * @return the name of the script the user has invoked */
    protected String cliScriptName() {
        return "cloud-explorer";
    }

    /** method intended for overriding when a different {@link Cli} is desired,
     * or when the subclass wishes to change any of the arguments */
    protected CliBuilder<BrooklynCommand> cliBuilder() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.<BrooklynCommand>builder(cliScriptName())
                .withDescription("Brooklyn Management Service")
                .withCommands(
                        HelpCommand.class,
                        InfoCommand.class);

        builder.withGroup("compute")
                .withDescription("Access cloud-compute details of a given cloud")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(ImmutableList.<Class<? extends BrooklynCommand>>of(
                        ComputeListImagesCommand.class,
                        ComputeListHardwareProfilesCommand.class,
                        ComputeListInstancesCommand.class,
                        ComputeGetImageCommand.class,
                        ComputeDefaultTemplateCommand.class,
                        ComputeTerminateInstancesCommand.class));

        builder.withGroup("blobstore")
                .withDescription("Access cloud-blobstore details of a given cloud")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(ImmutableList.<Class<? extends BrooklynCommand>>of(
                        BlobstoreListContainersCommand.class, 
                        BlobstoreListContainerCommand.class,
                        BlobstoreGetBlobCommand.class));

        return builder;
    }
    
    protected void execCli(String ...args) {
        execCli(cliBuilder().build(), args);
    }
    
    protected void execCli(Cli<BrooklynCommand> parser, String ...args) {
        try {
            log.debug("Parsing command line arguments: {}", Arrays.asList(args));
            BrooklynCommand command = parser.parse(args);
            log.debug("Executing command: {}", command);
            command.call();
            System.exit(SUCCESS);
        } catch (ParseException pe) { // looks like the user typed it wrong
            System.err.println("Parse error: " + pe.getMessage()); // display
                                                                   // error
            System.err.println(getUsageInfo(parser)); // display cli help
            System.exit(PARSE_ERROR);
        } catch (FatalConfigurationRuntimeException e) {
            log.error("Configuration error: "+e.getMessage(), e.getCause());
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(CONFIGURATION_ERROR);
        } catch (FatalRuntimeException e) { // anticipated non-configuration error
            log.error("Startup error: "+e.getMessage(), e.getCause());
            System.err.println("Startup error: "+e.getMessage());
            System.exit(EXECUTION_ERROR);
        } catch (Exception e) { // unexpected error during command execution
            log.error("Execution error: " + e.getMessage(), e);
            System.err.println("Execution error: " + e.getMessage());
            if (!(e instanceof UserFacingException))
                e.printStackTrace();
            System.exit(EXECUTION_ERROR);
        }
    }

    protected String getUsageInfo(Cli<BrooklynCommand> parser) {
        StringBuilder help = new StringBuilder();
        help.append("\n");
        Help.help(parser.getMetadata(), Collections.<String>emptyList(), help);
        return help.toString();
    }

}
