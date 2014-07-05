package brooklyn.management.classloading;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.guava.Maybe;

public final class BrooklynClassLoadingContextSequential extends AbstractBrooklynClassLoadingContext {

    private static final Logger log = LoggerFactory.getLogger(BrooklynClassLoadingContextSequential.class);
    
    private final List<BrooklynClassLoadingContext> primaries = MutableList.<BrooklynClassLoadingContext>of();
    // secondaries used to put java classloader last
    private final Set<BrooklynClassLoadingContext> secondaries = MutableSet.<BrooklynClassLoadingContext>of();

    public BrooklynClassLoadingContextSequential(ManagementContext mgmt, BrooklynClassLoadingContext ...targets) {
        super(mgmt);
        for (BrooklynClassLoadingContext target: targets)
            add(target);
    }
    
    public void add(BrooklynClassLoadingContext target) {
        if (target instanceof BrooklynClassLoadingContextSequential) {
            for (BrooklynClassLoadingContext targetN: ((BrooklynClassLoadingContextSequential)target).primaries )
                add(targetN);
            for (BrooklynClassLoadingContext targetN: ((BrooklynClassLoadingContextSequential)target).secondaries )
                addSecondary(targetN);
        } else {
            this.primaries.add( target );
        }
    }

    /** @since 0.7.0 only for supporting legacy java-classloading based catalog */
    @Deprecated
    public void addSecondary(BrooklynClassLoadingContext target) {
        if (!(target instanceof JavaBrooklynClassLoadingContext)) {
            // support for legacy catalog classloader only
            log.warn("Only Java classloaders should be secondary");
        }
        this.secondaries.add( target );
    }
    
    public Maybe<Class<?>> tryLoadClass(String className) {
        for (BrooklynClassLoadingContext target: primaries) {
            Maybe<Class<?>> clazz = target.tryLoadClass(className);
            if (clazz.isPresent())
                return clazz;
        }
        for (BrooklynClassLoadingContext target: secondaries) {
            Maybe<Class<?>> clazz = target.tryLoadClass(className);
            if (clazz.isPresent())
                return clazz;
        }

        return Maybe.absent("Unable to load "+className+" from "+primaries);
    }

    @Override
    public String toString() {
        return "classload:"+primaries+";"+secondaries;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), primaries, secondaries);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof BrooklynClassLoadingContextSequential)) return false;
        if (!Objects.equal(primaries, ((BrooklynClassLoadingContextSequential)obj).primaries)) return false;
        if (!Objects.equal(secondaries, ((BrooklynClassLoadingContextSequential)obj).secondaries)) return false;
        return true;
    }
    
}
