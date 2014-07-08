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
package brooklyn.location.jclouds.templates;

import java.util.ArrayList;
import java.util.List;

import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

public abstract class AbstractPortableTemplateBuilder<T extends AbstractPortableTemplateBuilder<?>> implements TemplateBuilder {

    /** list of commands supplied by user, excluding options */
    protected List<Function<TemplateBuilder,TemplateBuilder>> commands = new ArrayList<Function<TemplateBuilder,TemplateBuilder>>();
    
    private Hardware hardware;
    private Image image;
    private Template template;
    private String locationId;
    private String imageId;
    private String hardwareId;
    private OsFamily os;
    private String osNameRegex;
    private String osDescriptionRegex;
    private String osVersionRegex;
    private String osArchitectureRegex;
    private String hypervisorRegex;
    private Boolean is64bit;
    private String imageNameRegex;
    private String imageDescriptionRegex;
    private String imageVersionRegex;
    private Double minCores;
    private Integer minRam;
    private Double minDisk;
    private Predicate<Image> imageCondition;
    private Function<Iterable<? extends Image>, Image> imageChooserFunction;
    /** this is the last options instance set by a call to options(TemplateOptions) */
    private TemplateOptions options;
    /** these are extra options that we want _added_, in order, on top of the last options set */
    private List<TemplateOptions> additionalOptions = new ArrayList<TemplateOptions>();
    
    @Override
    public T any() {
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.any(); }});
        return (T)this;
    }

    @Override
    public T fromHardware(final Hardware hardware) {
        this.hardware = hardware;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.fromHardware(hardware); }});
        return (T)this;
    }

    public Hardware getHardware() {
        return hardware;
    }

    @Override
    public T fromImage(final Image image) {
        this.image = image;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.fromImage(image); }});
        return (T)this;
    }
    
    public Image getImage() {
        return image;
    }

    @Override
    public T fromTemplate(final Template template) {
        this.template = template;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.fromTemplate(template); }});
        return (T)this;
    }
    
    public Template getTemplate() {
        return template;
    }

    @Override
    public T smallest() {
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.smallest(); }});
        return (T)this;
    }

    @Override
    public T fastest() {
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.fastest(); }});
        return (T)this;
    }

    @Override
    public T biggest() {
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.biggest(); }});
        return (T)this;
    }

    @Override
    public T locationId(final String locationId) {
        this.locationId = locationId;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.locationId(locationId); }});
        return (T)this;
    }
    
    public String getLocationId() {
        return locationId;
    }

    @Override
    public T imageId(final String imageId) {
        this.imageId = imageId;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.imageId(imageId); }});
        return (T)this;
    }
    
    public String getImageId() {
        return imageId;
    }

    @Override
    public T hardwareId(final String hardwareId) {
        this.hardwareId = hardwareId;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.hardwareId(hardwareId); }});
        return (T)this;
    }
    
    public String getHardwareId() {
        return hardwareId;
    }

    @Override
    public T osNameMatches(final String osNameRegex) {
        this.osNameRegex = osNameRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.osNameMatches(osNameRegex); }});
        return (T)this;
    }
    
    public String getOsNameMatchesRegex() {
        return osNameRegex;
    }

    @Override
    public T osDescriptionMatches(final String osDescriptionRegex) {
        this.osDescriptionRegex = osDescriptionRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.osDescriptionMatches(osDescriptionRegex); }});
        return (T)this;
    }
    
    public String getOsDescriptionMatchesRegex() {
        return osDescriptionRegex;
    }

    @Override
    public T osVersionMatches(final String osVersionRegex) {
        this.osVersionRegex = osVersionRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.osVersionMatches(osVersionRegex); }});
        return (T)this;
    }
    
    public String getOsVersionMatchesRegex() {
        return osVersionRegex;
    }

    @Override
    public T osArchMatches(final String osArchitectureRegex) {
        this.osArchitectureRegex = osArchitectureRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.osArchMatches(osArchitectureRegex); }});
        return (T)this;
    }
    
    public String getOsArchitectureMatchesRegex() {
        return osArchitectureRegex;
    }

    @Override
    public T os64Bit(final boolean is64bit) {
        this.is64bit = is64bit;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.os64Bit(is64bit); }});
        return (T)this;
    }
    
    public Boolean getIs64bit() {
        return is64bit;
    }

    @Override
    public T osFamily(final OsFamily os) {
        this.os = os;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.osFamily(os); }});
        return (T)this;
    }
    
    public OsFamily getOsFamily() {
        return os;
    }

    @Override
    public T hypervisorMatches(final String hypervisorRegex) {
        this.hypervisorRegex = hypervisorRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.hypervisorMatches(hypervisorRegex); }});
        return (T)this;
    }
    
    public String getHypervisorMatchesRegex() {
        return hypervisorRegex;
    }

    @Override
    public T imageNameMatches(final String imageNameRegex) {
        this.imageNameRegex = imageNameRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.imageNameMatches(imageNameRegex); }});
        return (T)this;
    }
    
    public String getImageNameMatchesRegex() {
        return imageNameRegex;
    }

    @Override
    public T imageDescriptionMatches(final String imageDescriptionRegex) {
        this.imageDescriptionRegex = imageDescriptionRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.imageDescriptionMatches(imageDescriptionRegex); }});
        return (T)this;
    }
    
    public String getImageDescriptionMatchesRegex() {
        return imageDescriptionRegex;
    }

    @Override
    public T imageVersionMatches(final String imageVersionRegex) {
        this.imageVersionRegex = imageVersionRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.imageVersionMatches(imageVersionRegex); }});
        return (T)this;
    }
    
    public String getImageVersionMatchesRegex() {
        return imageVersionRegex;
    }
    
    @Override
    public T imageMatches(final Predicate<Image> condition) {
        this.imageCondition = condition;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.imageMatches(condition); }});
        return (T)this;
    }
    
    public Predicate<Image> getImageMatchesCondition() {
        return imageCondition;
    }

    @Override
    public T minCores(final double minCores) {
        this.minCores = minCores;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.minCores(minCores); }});
        return (T)this;
    }
    
    public Double getMinCores() {
        return minCores;
    }

    @Override
    public T minRam(final int megabytes) {
        this.minRam = megabytes;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.minRam(megabytes); }});
        return (T)this;
    }
    
    /** megabytes */
    public Integer getMinRam() {
        return minRam;
    }
    
    @Override
    public T minDisk(final double gigabytes) {
        this.minDisk = gigabytes;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.minDisk(gigabytes); }});
        return (T)this;
    }

    /** megabytes */
    public Double getMinDisk() {
        return minDisk;
    }

    public T imageChooser(final Function<Iterable<? extends Image>, Image> imageChooserFunction) {
        this.imageChooserFunction = imageChooserFunction;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.imageChooser(imageChooserFunction); }});
        return (T)this;
    }
    
    public Function<Iterable<? extends Image>, Image> imageChooser() {
        return imageChooserFunction;
    }

    /** clears everything set in this template, including any default from the compute service */
    // not sure this is that useful, as the default is only applied if there are no changes
    public T blank() {
        hardware = null;
        image = null;
        template = null;
        hypervisorRegex = null;
        os = null;
        locationId = null;
        imageId = null;
        hardwareId = null;
        osNameRegex = null;
        osDescriptionRegex = null;
        osVersionRegex = null;
        osArchitectureRegex = null;
        is64bit = null;
        imageNameRegex = null;
        imageDescriptionRegex = null;
        imageVersionRegex = null;
        imageCondition = null;
        minCores = null;
        minRam = null;
        options = null;
        additionalOptions.clear();

        // clear all fields, and commands
        commands.clear();
        // then add a command to clear osName + Version + 64bit
        osFamily(null);
        osVersionMatches(null);
        // no way to turn off 64-bitness, but it won't usually be turned on
//        os64bit(null);
        // set _something_ to prevent the default from applying
        minRam(1);

        return (T)this;
    }
    
    /** true if the templateBuilder spec is blank (ignoring customization options e.g. tags for the resulting instance) */
    public boolean isBlank() {
        if (commands.isEmpty()) return true;
        //also "blank" if we've blanked it
        if (commands.size()==1 && (minRam!=null && minRam==1)) return true;
        return false;
    }
    
    @Override
    public T options(final TemplateOptions options) {
        this.options = options;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.options(options); }});
        return (T)this;
    }

    /** sets customization options; may be null if not set. use addOptions(new TemplateOptions()) to set new ones. */
    public TemplateOptions getOptions() {
        return options;
    }
    
    /** adds customization options; if options have already been set, this will additively set selected options
     * (but not all, see addTemplateOptions for more info)
     */
    public T addOptions(final TemplateOptions options) {
        this.additionalOptions.add(options);
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.options(options); }});
        return (T)this;
    }

    public List<TemplateOptions> getAdditionalOptions() {
        return ImmutableList.copyOf(additionalOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                hypervisorRegex,
                os,
                locationId,
                hardwareId,
                imageId,
                imageDescriptionRegex,
                imageNameRegex,
                imageVersionRegex,
                // might not be implement hashCode, so ignore
//                imageCondition,
//                imageChooserFunction,
                is64bit,
                locationId,
                osArchitectureRegex,
                osDescriptionRegex,
                osNameRegex,
                osVersionRegex,
                minCores,
                minRam,
                minDisk,
                options,
                additionalOptions,
                // might not implement hashCode, so ignore
//                template,
                0);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        AbstractPortableTemplateBuilder other = (AbstractPortableTemplateBuilder) obj;
        if (!Objects.equal(additionalOptions, other.additionalOptions)) return false;
        if (!Objects.equal(commands, other.commands)) return false;
        if (!Objects.equal(locationId, other.locationId)) return false;
        if (!Objects.equal(hardware, other.hardware)) return false;
        if (!Objects.equal(hardwareId, other.hardwareId)) return false;
        if (!Objects.equal(image, other.image)) return false;
        if (!Objects.equal(imageId, other.imageId)) return false;
        if (!Objects.equal(imageDescriptionRegex, other.imageDescriptionRegex)) return false;
        if (!Objects.equal(imageNameRegex, other.imageNameRegex)) return false;
        if (!Objects.equal(imageVersionRegex, other.imageVersionRegex)) return false;
        if (!Objects.equal(imageCondition, other.imageCondition)) return false;
        if (!Objects.equal(imageChooserFunction, other.imageChooserFunction)) return false;
        if (!Objects.equal(os, other.os)) return false;
        if (!Objects.equal(osArchitectureRegex, other.osArchitectureRegex)) return false;
        if (!Objects.equal(osDescriptionRegex, other.osDescriptionRegex)) return false;
        if (!Objects.equal(osNameRegex, other.osNameRegex)) return false;
        if (!Objects.equal(osVersionRegex, other.osVersionRegex)) return false;
        if (!Objects.equal(is64bit, other.is64bit)) return false;
        if (!Objects.equal(hypervisorRegex, other.hypervisorRegex)) return false;
        if (!Objects.equal(minCores, other.minCores)) return false;
        if (!Objects.equal(minRam, other.minRam)) return false;
        if (!Objects.equal(minDisk, other.minDisk)) return false;
        if (!Objects.equal(options, other.options)) return false;
        if (!Objects.equal(template, other.template)) return false;
        return true;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+makeNonTrivialArgumentsString()+"]";
    }

    protected String makeNonTrivialArgumentsString() {
        String s =
                  (hardware != null ? "hardware=" + hardware + ", " : "")
                + (image != null ? "image=" + image + ", " : "")
                + (template != null ? "template=" + template + ", " : "")
                + (hypervisorRegex != null ? "hypervisorRegex="
                        + hypervisorRegex + ", " : "")
                + (os != null ? "os=" + os + ", " : "")
                + (locationId != null ? "locationId=" + locationId + ", " : "")
                + (imageId != null ? "imageId=" + imageId + ", " : "")
                + (hardwareId != null ? "hardwareId=" + hardwareId + ", " : "")
                + (osNameRegex != null ? "osNameRegex=" + osNameRegex + ", "
                        : "")
                + (osDescriptionRegex != null ? "osDescriptionRegex="
                        + osDescriptionRegex + ", " : "")
                + (osVersionRegex != null ? "osVersionRegex=" + osVersionRegex
                        + ", " : "")
                + (osArchitectureRegex != null ? "osArchictectureRegex="
                        + osArchitectureRegex + ", " : "")
                + (is64bit != null ? "is64bit=" + is64bit + ", " : "")
                + (imageNameRegex != null ? "imageNameRegex=" + imageNameRegex
                        + ", " : "")
                + (imageDescriptionRegex != null ? "imageDescriptionRegex="
                        + imageDescriptionRegex + ", " : "")
                + (imageVersionRegex != null ? "imageVersionRegex="
                        + imageVersionRegex + ", " : "")
                + (imageCondition != null ? "imageCondition=" + imageCondition
                        + ", " : "")
                + (imageChooserFunction != null ? "imageChooserFunction=" + imageChooserFunction
                        + ", " : "")
                + (minCores != null ? "minCores=" + minCores + ", " : "")
                + (minRam != null ? "minRam=" + minRam + ", " : "")
                + (minDisk != null ? "minDisk=" + minDisk + ", " : "");
        if (s.endsWith(", ")) s = s.substring(0, s.length()-2);
        return s;
    }    

    
}
