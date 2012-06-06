Brooklyn.summary = (function() {
    function SummaryTab() {
        this.id = 'summary';

        this.update = function() {
            if (typeof this.entity_id !== 'undefined') {
                $.getJSON("../entity/info?id=" + this.entity_id, this.handleSummaryData).error(
                    function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get entity info to show in summary.");});
            }
        };

        this.handleSummaryData = function(json) {
            var name_html = '<p><span class="label">Name:</span> ' + json.displayName + "</p>";
            var locations_html = '<h4>Locations</h4>';
            if (json.locations.length > 0) {
                locations_html += '<ul id="#summary-locations">\n';
                for (l in json.locations){
                    locations_html += "<li>"+json.locations[l].displayName
                    for (k in json.locations[l].parentLocations) {
                        locations_html += ", "+json.locations[l].parentLocations[k];
                    }
                    locations_html += "</li>\n";
                }
                locations_html += "</ul>";
            } else {
                locations_html += "None set";
            }

            $("#summary-basic-info").html(name_html + locations_html);

            var status_html = '<p><span class="label">Status: </span>'+json.status+'</p><br/>';
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

            var identity_html = '<p>Self: '+json.id+'</p><br/>';
            var owner_html = '';
            if (json.ownerId) owner_html = '<p>Owner: '+json.ownerId+'</p><br/>';
            var app_html = '<p>App: '+json.applicationId+'</p>';
            $("#summary-activity").html('<h4>IDs</h4>'+identity_html + owner_html + app_html);

            $(Brooklyn.eventBus).trigger('update_ok');
        };

        this.makeHandlers();
    }

    SummaryTab.prototype = new Brooklyn.tabs.Tab();

    function init() {
        var summaryTab = new SummaryTab();

        $(Brooklyn.eventBus).bind("entity_selected", summaryTab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", summaryTab.handler.tabSelected);

        // The summary tab is special. It should start listening to updates
        // without being explicitly selected because it is shown by default.
        $(Brooklyn.eventBus).bind("update", summaryTab.handler.update);

        $('#summary').click(function() {
            Brooklyn.util.pauseUpdate(summaryTab);
        });
    }

   return {
       init: init
   };

})();

$(document).ready(Brooklyn.summary.init);
