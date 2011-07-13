Brooklyn.summary = (function() {
    // State
    var entity_id;

    function handleSummaryData(json) {
        // FIXME: Do stuff
        var name_html = "<p>Name: " + json.displayName + "</p>";

        var locations_html = '<div id="summary-locations">\nLocations: ';
        if (json.locations.length > 0) {
            locations_html += '<ul id="#summary-locations">\n<li>';
            locations_html += json.locations.join("</li>\n<li>");
            locations_html += "</ul>";
        } else {
            locations_html += "None set";
        }
        locations_html += '</div>';

        var groups_html = '<div id="summary-groups">Groups:';
        if (json.groupNames.length > 0) {
            groups_html += '<ul>\n<li>';
            groups_html += json.groupNames.join("</li>\n<li>");
            groups_html += "</ul>";
        } else {
            groups_html += "None";
        }

        var activity_html = '<div id="summary-activity"><h4>Recent Activity</h4>';
        activity_html += '<p>TODO</p>';
        activity_html += '</div>';

        $("#summary").html(name_html + locations_html + groups_html + activity_html);
    }

    function update() {
        if (typeof entity_id !== 'undefined') {
            $.getJSON("info?id=" + entity_id, handleSummaryData).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get entity info to show in summary.");});
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
