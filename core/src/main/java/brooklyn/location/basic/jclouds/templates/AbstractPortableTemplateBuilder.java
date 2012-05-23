package brooklyn.location.basic.jclouds.templates;

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

public abstract class AbstractPortableTemplateBuilder<T extends AbstractPortableTemplateBuilder> implements TemplateBuilder {

    /** list of commands supplied by user, excluding options */
    protected List<Function<TemplateBuilder,TemplateBuilder>> commands = new ArrayList<Function<TemplateBuilder,TemplateBuilder>>();
    
    private Hardware hardware;
    private Image image;
    private Template template;
    private String hypervisorRegex;
    private OsFamily os;
    private String locationId;
    private String imageId;
    private String hardwareId;
    private String osNameRegex;
    private String osDescriptionRegex;
    private String osVersionRegex;
    private String osArchitectureRegex;
    private Boolean is64bit;
    private String imageNameRegex;
    private String imageDescriptionRegex;
    private String imageVersionRegex;
    private Predicate<Image> imageCondition;
    private Double minCores;
    private Integer minRam;
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
    public T options(final TemplateOptions options) {
        this.options = options;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.options(options); }});
        return (T)this;
    }

    public TemplateOptions getOptions() {
        return options;
    }
    
    public T addOptions(final TemplateOptions options) {
        this.additionalOptions.add(options);
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.options(options); }});
        return (T)this;
    }

    public List<TemplateOptions> getAdditionalOptions() {
        return ImmutableList.copyOf(additionalOptions);
    }

    /** some fields don't implement hashcode, so we ignore them */
    @Override
    public int hashCode() {
        return Objects.hashCode(
                additionalOptions,
                hardwareId,
                hypervisorRegex,
//                imageCondition,
                imageDescriptionRegex,
                imageId,
                imageNameRegex,
                imageVersionRegex,
                is64bit,
                locationId,
                minCores,
                minRam,
                options,
                os,
                osArchitectureRegex,
                osDescriptionRegex,
                osNameRegex,
                osVersionRegex,
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
        if (!Objects.equal(hardware, other.hardware)) return false;
        if (!Objects.equal(hardwareId, other.hardwareId)) return false;
        if (!Objects.equal(hypervisorRegex, other.hypervisorRegex)) return false;
        if (!Objects.equal(image, other.image)) return false;
        if (!Objects.equal(imageCondition, other.imageCondition)) return false;
        if (!Objects.equal(imageDescriptionRegex, other.imageDescriptionRegex)) return false;
        if (!Objects.equal(imageId, other.imageId)) return false;
        if (!Objects.equal(imageNameRegex, other.imageNameRegex)) return false;
        if (!Objects.equal(imageVersionRegex, other.imageVersionRegex)) return false;
        if (!Objects.equal(is64bit, other.is64bit)) return false;
        if (!Objects.equal(locationId, other.locationId)) return false;
        if (!Objects.equal(minCores, other.minCores)) return false;
        if (!Objects.equal(minRam, other.minRam)) return false;
        if (!Objects.equal(options, other.options)) return false;
        if (!Objects.equal(os, other.os)) return false;
        if (!Objects.equal(osArchitectureRegex, other.osArchitectureRegex)) return false;
        if (!Objects.equal(osDescriptionRegex, other.osDescriptionRegex)) return false;
        if (!Objects.equal(osNameRegex, other.osNameRegex)) return false;
        if (!Objects.equal(osVersionRegex, other.osVersionRegex)) return false;
        if (!Objects.equal(template, other.template)) return false;
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
                + (minCores != null ? "minCores=" + minCores + ", " : "")
                + (minRam != null ? "minRam=" + minRam + ", " : "");
        if (s.endsWith(", ")) s = s.substring(0, s.length()-2);
        return s;
    }    

    
}
