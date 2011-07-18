/* Common parts of the web console. Should be loaded first.
 * 
 * Components can be put in their own files.
 */

/* Everything should be kept in here.
   e.g. Brooklyn.tabs contains the tabs module.
 */
var Brooklyn = {};

/* Used to bind jQuery custom events for the application. */
Brooklyn.eventBus = {};

Brooklyn.main = (function() {
    /* A timer for periodically refreshing data we display. */
    var updateInterval = 5000;

    function triggerUpdateEvent () {
        $(Brooklyn.eventBus).trigger('update');
    }

    setInterval(triggerUpdateEvent, updateInterval);

    /* Display the status of the management console.
     *
     * This is meant to be used to tell the user that the data is updating live
     * or that there is some error fetching it.
     *
     * These status line updating functions are called on the update_ok and
     * update_failed events. This is to allow many UI components to
     * signal their status easily. It doesn't deal with the case of one
     * component failing and another working very shortly afterwards. We
     * should have a think about this more but it is probably ok to start with.
     */
    function updateOK() {
        $("#status-message").html(currentTimeAsString());
    }

    // Yes, this is stupid. I don't know of a standard way to do it though.
    function zeroPad(i) {
        return (i < 10) ? "0" + i : i;
    }

    function currentTimeAsString() {
        // TODO: I don't know of a standard way to format dates. There seem to be libraries for it though. Meh.
        var date = new Date();
        return zeroPad(date.getHours()) + ":" + zeroPad(date.getMinutes()) + ":" + zeroPad(date.getSeconds());
    }

    function updateFailed(e, message) {
        $("#status-message").html("Error" + (message ? ": " + message : ""));
    }

    function init() {
        $(Brooklyn.eventBus).bind("update_ok", updateOK);
        $(Brooklyn.eventBus).bind("update_failed", updateFailed);
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.main.init);
