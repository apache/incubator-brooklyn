package brooklyn.rest.domain;

import java.util.Comparator;

/**
 * Useful comparators for domain objects
 */
public class SummaryComparators {
    
    private SummaryComparators() {}
    
    public static Comparator<HasName> nameComparator() {
        return new Comparator<HasName>() {
            @Override
            public int compare(HasName o1, HasName o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };
    }

    public static Comparator<HasId> idComparator() {
        return new Comparator<HasId>() {
            @Override
            public int compare(HasId o1, HasId o2) {
                return o1.getId().compareTo(o2.getId());
            }
        };
    }

}
