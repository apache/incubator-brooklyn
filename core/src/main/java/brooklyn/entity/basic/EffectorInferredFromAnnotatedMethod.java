package brooklyn.entity.basic;

/**
 * @deprecated will be deleted in 0.5.  now called MethodEffector.
 */
@Deprecated
public class EffectorInferredFromAnnotatedMethod<T> extends MethodEffector<T> {
    public EffectorInferredFromAnnotatedMethod(Class<?> whereEffectorDefined, String methodName) {
        super(whereEffectorDefined, methodName);
    }
    public EffectorInferredFromAnnotatedMethod(Class<?> whereEffectorDefined, String methodName, String description) {
        super(whereEffectorDefined, methodName, description);
    }
}
