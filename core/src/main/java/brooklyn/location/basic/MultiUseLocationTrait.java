package brooklyn.location.basic;

/** interface which allows multiple entities to co-operate when using a shared resource */
public interface MultiUseLocationTrait {

    /** acquires a mutex, if available, otherwise blocks on its becoming available;
     * caller must release after use */
    public void acquireMutex(String mutexId, String description);

    /** acquires a mutex and returns true, if available; otherwise immediately returns false;
     * caller must release after use if this returns true */
    public boolean tryAcquireMutex(String mutexId, String description);

    /** releases a mutex, triggering another thread to use it or cleaning it up if no one else is waiting;
     * this should only be called by the mutex owner (thread) */
    public void releaseMutex(String mutexId);
    
}
