package brooklyn.config.render;

/** Methods used when testing the {@link RendererHints} regiostry. */
public class TestRendererHints {

    /** Clear the registry. */
    public static void clearRegistry() {
        RendererHints.registry.clear();
    }
}
