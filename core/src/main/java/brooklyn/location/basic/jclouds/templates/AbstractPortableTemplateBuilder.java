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
    private String osArchictectureRegex;
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
        this.osArchictectureRegex = osArchitectureRegex;
        commands.add(new Function<TemplateBuilder,TemplateBuilder>() { 
            public TemplateBuilder apply(TemplateBuilder b) { return b.osArchMatches(osArchitectureRegex); }});
        return (T)this;
    }
    
    public String getOsArchictectureMatchesRegex() {
        return osArchictectureRegex;
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
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((additionalOptions == null) ? 0 : additionalOptions
                        .hashCode());
//        result = prime * result
//                + ((commands == null) ? 0 : commands.hashCode());
//        result = prime * result
//                + ((hardware == null) ? 0 : hardware.hashCode());
        result = prime * result
                + ((hardwareId == null) ? 0 : hardwareId.hashCode());
        result = prime * result
                + ((hypervisorRegex == null) ? 0 : hypervisorRegex.hashCode());
//        result = prime * result + ((image == null) ? 0 : image.hashCode());
//        result = prime * result
//                + ((imageCondition == null) ? 0 : imageCondition.hashCode());
        result = prime
                * result
                + ((imageDescriptionRegex == null) ? 0 : imageDescriptionRegex
                        .hashCode());
        result = prime * result + ((imageId == null) ? 0 : imageId.hashCode());
        result = prime * result
                + ((imageNameRegex == null) ? 0 : imageNameRegex.hashCode());
        result = prime
                * result
                + ((imageVersionRegex == null) ? 0 : imageVersionRegex
                        .hashCode());
        result = prime * result + ((is64bit == null) ? 0 : is64bit.hashCode());
        result = prime * result
                + ((locationId == null) ? 0 : locationId.hashCode());
        result = prime * result
                + ((minCores == null) ? 0 : minCores.hashCode());
        result = prime * result + ((minRam == null) ? 0 : minRam.hashCode());
        result = prime * result + ((options == null) ? 0 : options.hashCode());
        result = prime * result + ((os == null) ? 0 : os.hashCode());
        result = prime
                * result
                + ((osArchictectureRegex == null) ? 0 : osArchictectureRegex
                        .hashCode());
        result = prime
                * result
                + ((osDescriptionRegex == null) ? 0 : osDescriptionRegex
                        .hashCode());
        result = prime * result
                + ((osNameRegex == null) ? 0 : osNameRegex.hashCode());
        result = prime * result
                + ((osVersionRegex == null) ? 0 : osVersionRegex.hashCode());
//        result = prime * result
//                + ((template == null) ? 0 : template.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractPortableTemplateBuilder other = (AbstractPortableTemplateBuilder) obj;
        if (additionalOptions == null) {
            if (other.additionalOptions != null)
                return false;
        } else if (!additionalOptions.equals(other.additionalOptions))
            return false;
        if (commands == null) {
            if (other.commands != null)
                return false;
        } else if (!commands.equals(other.commands))
            return false;
        if (hardware == null) {
            if (other.hardware != null)
                return false;
        } else if (!hardware.equals(other.hardware))
            return false;
        if (hardwareId == null) {
            if (other.hardwareId != null)
                return false;
        } else if (!hardwareId.equals(other.hardwareId))
            return false;
        if (hypervisorRegex == null) {
            if (other.hypervisorRegex != null)
                return false;
        } else if (!hypervisorRegex.equals(other.hypervisorRegex))
            return false;
        if (image == null) {
            if (other.image != null)
                return false;
        } else if (!image.equals(other.image))
            return false;
        if (imageCondition == null) {
            if (other.imageCondition != null)
                return false;
        } else if (!imageCondition.equals(other.imageCondition))
            return false;
        if (imageDescriptionRegex == null) {
            if (other.imageDescriptionRegex != null)
                return false;
        } else if (!imageDescriptionRegex.equals(other.imageDescriptionRegex))
            return false;
        if (imageId == null) {
            if (other.imageId != null)
                return false;
        } else if (!imageId.equals(other.imageId))
            return false;
        if (imageNameRegex == null) {
            if (other.imageNameRegex != null)
                return false;
        } else if (!imageNameRegex.equals(other.imageNameRegex))
            return false;
        if (imageVersionRegex == null) {
            if (other.imageVersionRegex != null)
                return false;
        } else if (!imageVersionRegex.equals(other.imageVersionRegex))
            return false;
        if (is64bit == null) {
            if (other.is64bit != null)
                return false;
        } else if (!is64bit.equals(other.is64bit))
            return false;
        if (locationId == null) {
            if (other.locationId != null)
                return false;
        } else if (!locationId.equals(other.locationId))
            return false;
        if (minCores == null) {
            if (other.minCores != null)
                return false;
        } else if (!minCores.equals(other.minCores))
            return false;
        if (minRam == null) {
            if (other.minRam != null)
                return false;
        } else if (!minRam.equals(other.minRam))
            return false;
        if (options == null) {
            if (other.options != null)
                return false;
        } else if (!options.equals(other.options))
            return false;
        if (os != other.os)
            return false;
        if (osArchictectureRegex == null) {
            if (other.osArchictectureRegex != null)
                return false;
        } else if (!osArchictectureRegex.equals(other.osArchictectureRegex))
            return false;
        if (osDescriptionRegex == null) {
            if (other.osDescriptionRegex != null)
                return false;
        } else if (!osDescriptionRegex.equals(other.osDescriptionRegex))
            return false;
        if (osNameRegex == null) {
            if (other.osNameRegex != null)
                return false;
        } else if (!osNameRegex.equals(other.osNameRegex))
            return false;
        if (osVersionRegex == null) {
            if (other.osVersionRegex != null)
                return false;
        } else if (!osVersionRegex.equals(other.osVersionRegex))
            return false;
        if (template == null) {
            if (other.template != null)
                return false;
        } else if (!template.equals(other.template))
            return false;
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
                + (osArchictectureRegex != null ? "osArchictectureRegex="
                        + osArchictectureRegex + ", " : "")
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
