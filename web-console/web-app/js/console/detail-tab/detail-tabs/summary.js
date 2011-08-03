Brooklyn.summary = (function() {
    var id = 'summary';

    // State
    var entity_id;

    function handleSummaryData(json) {
        var name_html = '<p><span class="label">Name:</span> ' + json.displayName + "</p>";
        var locations_html = '<h4>Locations</h4>';
        if (json.locations.length > 0) {
            locations_html += '<ul id="#summary-locations">\n';
            for (l in json.locations){
                locations_html += "<li>"+json.locations[l].displayName+"</li>\n";
            }
            locations_html += "</ul>";
        } else {
            locations_html += "None set";
        }
        //alert(locations_html)
        $("#summary-basic-info").html(name_html + locations_html) ;

        var status_html = '<span class="label">Status: </span>TODO';
        $("#summary-status").html(status_html);

        var groups_html = '<h4>Groups</h4>';
        if (json.groupNames.length > 0) {
            groups_html += '<ul>\n<li>';
            groups_html += json.groupNames.join("</li>\n<li>");
            groups_html += "</ul>";
        } else {
            groups_html += "None";
        }
        $("#summary-groups").html(groups_html);

        var activity_html = '<h4>Recent Activity</h4>';
        activity_html += '<p>TODO</p>';
        $("#summary-activity").html(activity_html);

        $(Brooklyn.eventBus).trigger('update_ok');
    }

    function update() {
        if (typeof entity_id !== 'undefined') {
            $.getJSON("../entity/info?id=" + entity_id, handleSummaryData).error(
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

    function tabSelectedHandler(e, tab_id) {
        tabSelected(tab_id, id);
    }

    function tabSelected(tab_id, my_tab_id) {
        console.log(tab_id + "   " + id);
        if (tab_id === id) {
            console.log("binding");
            update();
            $(Brooklyn.eventBus).bind("update", update);
        } else {
            console.log("unbinding");
            $(Brooklyn.eventBus).unbind("update", update);
        }
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", setEntityIdAndUpdate);
        $(Brooklyn.eventBus).bind("tab_selected", tabSelectedHandler);

        // The summary tab is special. It will start listening to updates
        // without being explicitly selected because it is shown by default.
        $(Brooklyn.eventBus).bind("update", update);
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.summary.init);
