Brooklyn.summary = (function() {
    // State
    var entity_id;

    function update() {
        if (entity_id) {
            // FIXME: Do stuff
            $("#summary").html("You see a summary of the entity and are impressed with the shiny management console.");
        }
    }

    /* This method is intended to be called as an event handler. The e paramater is
     * unused.
     */
    function setEntityIdAndUpdate(e, id) {
        entity_id = id;
        update();
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", setEntityIdAndUpdate);
        $(Brooklyn.eventBus).bind("update", update);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.summary.init);
