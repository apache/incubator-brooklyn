package brooklyn.util.mutex;

/** interface which allows multiple callers to co-operate using named mutexes, inspectably,
 * and containing implementation as inner class
 * <p>
 * MutexSupport is a common implementation of this.
 * mixin code frequently delegates to this, 
 * as shown in the test case's WithMutexesTest.SampleWithMutexesDelegatingMixin class 
 **/
public interface WithMutexes {

    /** returns true if the calling thread has the mutex with the given ID */
    public boolean hasMutex(String mutexId);
    
    /** acquires a mutex, if available, otherwise blocks on its becoming available;
     * caller must release after use */
    public void acquireMutex(String mutexId, String description) throws InterruptedException;

    /** acquires a mutex and returns true, if available; otherwise immediately returns false;
     * caller must release after use if this returns true */
    public boolean tryAcquireMutex(String mutexId, String description);

    /** releases a mutex, triggering another thread to use it or cleaning it up if no one else is waiting;
     * this should only be called by the mutex owner (thread) */
    public void releaseMutex(String mutexId);
    
}
