package org.overpaas.util;

/** 
 * Convenience for retrieving well-defined system properties, including checking if they have been set etc.
 */
public class OverpaasSystemProperties {

	public static BooleanSystemProperty DEBUG = new BooleanSystemProperty("com.cloudsoftcorp.brooklyn.debug");
	public static BooleanSystemProperty EXPERIMENTAL = new BooleanSystemProperty("com.cloudsoftcorp.brooklyn.experimental");

	public static class StringSystemProperty {
		public StringSystemProperty(String name) {
			this.propertyName = name;
		}

		private final String propertyName;

		public String getPropertyName() {
			return propertyName;
		}

		public boolean isAvailable() {
			String property = System.getProperty(getPropertyName());
			return property!=null;
		}
		public boolean isNonEmpty() {
			String property = System.getProperty(getPropertyName());
			return property!=null && !property.equals("");
		}

		public String getValue() {
			return System.getProperty(getPropertyName());
		}
		
	    @Override
	    public String toString() {
	    	return getPropertyName()+(isAvailable()?"="+getValue():"(unset)");
	    }
	}

	private static class BasicDelegatingSystemProperty {
        protected final StringSystemProperty delegate;
        
        public BasicDelegatingSystemProperty(String name) {
            delegate = new StringSystemProperty(name);
        }
        public String getPropertyName() {
            return delegate.getPropertyName();
        }
        public boolean isAvailable() {
            return delegate.isAvailable();
        }
        public String toString() {
            return delegate.toString();
        }
    }
	
	public static class BooleanSystemProperty extends BasicDelegatingSystemProperty {
		public BooleanSystemProperty(String name) {
			super(name);
		}
		public boolean isEnabled() {
			// actually access system property!
			return Boolean.getBoolean(getPropertyName());
		}
	}

    public static class IntegerSystemProperty extends BasicDelegatingSystemProperty {
        public IntegerSystemProperty(String name) {
            super(name);
        }
        public int getValue() {
            return Integer.parseInt(delegate.getValue());
        }
    }

    public static class DoubleSystemProperty extends BasicDelegatingSystemProperty {
        public DoubleSystemProperty(String name) {
            super(name);
        }
        public double getValue() {
            return Double.parseDouble(delegate.getValue());
        }
    }
}
