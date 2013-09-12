package brooklyn.entity.basic;

import java.util.Date;

/**
 */
public class ApplicationEvent {
    private final Date date;
    private final Lifecycle event;
    private final String applicationId;
    private final String applicationName;
    private final String applicationType;

    public ApplicationEvent(Lifecycle event, String applicationId, String applicationName, String applicationType) {
        this.date = new Date();
        this.event = event;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.applicationType = applicationType;
    }

    public Date getDate() {
        return date;
    }

    public Lifecycle getEvent() {
        return event;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getApplicationType() {
        return applicationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ApplicationEvent that = (ApplicationEvent) o;

        if (applicationId != null ? !applicationId.equals(that.applicationId) : that.applicationId != null)
            return false;
        if (applicationName != null ? !applicationName.equals(that.applicationName) : that.applicationName != null)
            return false;
        if (applicationType != null ? !applicationType.equals(that.applicationType) : that.applicationType != null)
            return false;
        if (date != null ? !date.equals(that.date) : that.date != null) return false;
        if (event != that.event) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = date != null ? date.hashCode() : 0;
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (applicationId != null ? applicationId.hashCode() : 0);
        result = 31 * result + (applicationName != null ? applicationName.hashCode() : 0);
        result = 31 * result + (applicationType != null ? applicationType.hashCode() : 0);
        return result;
    }
}
