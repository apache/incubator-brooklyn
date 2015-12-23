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
package org.apache.brooklyn.cli;

import static com.google.common.base.Preconditions.checkNotNull;
import io.airlift.command.Command;
import io.airlift.command.Option;
import io.airlift.command.ParseException;

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
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsUtil;
import org.apache.brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import org.apache.brooklyn.util.stream.Streams;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
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

    public static abstract class JcloudsCommand extends AbstractMain.BrooklynCommandCollectingArgs {
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
            
            Template template = loc.buildTemplate(computeService, loc.config().getBag());
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
            
            BlobStoreContext context = JcloudsUtil.newBlobstoreContext(provider, endpoint, identity, credential);
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
}
