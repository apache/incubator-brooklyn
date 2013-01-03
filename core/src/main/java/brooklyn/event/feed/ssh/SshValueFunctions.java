package brooklyn.event.feed.ssh;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.gson.JsonElement;

public class SshValueFunctions {

    public static Function<SshPollValue, Integer> exitStatus() {
        return new Function<SshPollValue, Integer>() {
            @Override public Integer apply(SshPollValue input) {
                return input.getExitStatus();
            }
        };
    }

    public static Function<SshPollValue, String> stdout() {
        return new Function<SshPollValue, String>() {
            @Override public String apply(SshPollValue input) {
                return input.getStdout();
            }
        };
    }
    
    public static Function<SshPollValue, String> stderr() {
        return new Function<SshPollValue, String>() {
            @Override public String apply(SshPollValue input) {
                return input.getStderr();
            }
        };
    }
    
    public static Function<SshPollValue, Boolean> exitStatusEquals(final int expected) {
        return chain(SshValueFunctions.exitStatus(), Functions.forPredicate(Predicates.equalTo(expected)));
    }

    // TODO Do we want these chain methods? Does guava have them already? Duplicated in HttpValueFunctions.
    public static <A,B,C> Function<A,C> chain(final Function<A,? extends B> f1, final Function<B,C> f2) {
        return new Function<A,C>() {
            @Override public C apply(@Nullable A input) {
                return f2.apply(f1.apply(input));
            }
        };
    }
    
    public static <A,B,C,D> Function<A,D> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        return new Function<A,D>() {
            @Override public D apply(@Nullable A input) {
                return f3.apply(f2.apply(f1.apply(input)));
            }
        };
    }
}
