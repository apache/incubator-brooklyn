package brooklyn.entity.basic;

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import com.google.common.base.CaseFormat

/**
 * An enumeration representing the different provisioning mechanisms for an {@link Entity}.
 */
public enum Provisioning {
    SCRIPT,
    PALLET,
    CHEF,
    PUPPET,
    PACKAGE_MANAGER,
    IMAGE,
    VIRTUAL_MACHINE,
    OSGI_BINDLE,
    GIT_REPOSITORY,
    NONE;
    
    /**
     * The text representation of the {@link #name()}.
     *
     * This is formatted as lower case characters, with hyphens instead of spaces.
     */
    public String value() {
       return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name());
    }

    /** @see #value() */
    @Override
    public String toString() { return value(); }

    /**
     * Creates a {@link Lifecycle} from a text representation.
     *
     * This accepts the text representations output by the {@link #value()} method for each entry.
     *
     * @see #value()
     */
    public static Provisioning fromValue(String v) {
       try {
          return valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, v));
       } catch (IllegalArgumentException iae) {
          return NONE;
       }
    }
}

public interface ProvisioningAware { }
public interface ProvisioningSupport { }

public interface ScriptAware extends ProvisioningAware { }
public interface ScriptSupport extends ProvisioningSupport { }

public interface PalletAware { }
public interface PalletSupport { }

public interface ChefAware { }
public interface ChefSupport { }

public interface PuppetAware { }
public interface PuppetSupport { }

public interface PackageManagerAware { }
public interface PackageManagerSupport { }

public interface ImageAware { }
public interface ImageSupport { }

public interface VirtualMachineAware { }
public interface VirtualMachineSupport { }

public interface OsgiBundleAware { }
public interface OsgiBundleSupport { }

public interface GitRepositoryAware { }
public interface GitRepositorySupport { }