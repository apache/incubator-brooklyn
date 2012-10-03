package brooklyn.util;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CompoundRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 6110995537064639587L;

    private final List<Throwable> causes;

    public CompoundRuntimeException(String message) {
        super(message);
        this.causes = Collections.emptyList();
    }

    public CompoundRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.causes = (cause == null) ? Collections.<Throwable>emptyList() : Collections.singletonList(cause);
    }

    public CompoundRuntimeException(Throwable cause) {
        super(cause);
        this.causes = (cause == null) ? Collections.<Throwable>emptyList() : Collections.singletonList(cause);
    }

    public CompoundRuntimeException(String message, Iterable<? extends Throwable> causes) {
        super(message, (Iterables.isEmpty(causes) ? null : Iterables.get(causes, 0)));
        this.causes = ImmutableList.copyOf(causes);
    }

    public List<Throwable> getAllCauses() {
        return causes;
    }
}
