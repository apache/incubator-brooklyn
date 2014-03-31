package brooklyn.util.internal;

/** Maven Central requires javadoc to promote as a release. This seemed to happen when this was built by maven as a bundle,
 * but now that it is built as a jar it does not. This class exists only to provide that javadoc.
 * <p>
 * Note the groovy code does javadoc but the maven build is not picking it up. It *is* generated as part of the site build.
 */
public class JavadocDummy {

    private JavadocDummy() {}
    
}
