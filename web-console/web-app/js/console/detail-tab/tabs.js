Brooklyn.tabs = (function() {
    function enableTabs() {
        $("#subtabs").tabs("option", "disabled", false);
    }

    function disableTabs() {
        // Should be able to use true here instead of list but it was not working.
        // Must extend the list to the number of tabs used.
        $("#subtabs").tabs("option", "disabled", [0,1,2,3,4,5,6,7,8,9,10]);
    }

    function init() {
        $("#subtabs").tabs({
            show: function(event, ui) {
                $(Brooklyn.eventBus).trigger('tab_selected', ui.panel.id);
            }
        });

        disableTabs();

        var selectEntityMessage = "<p>Select an entity.</p>";
        $('#summary-basic-info').html(selectEntityMessage);
        location.hash = "#summary";

        $(Brooklyn.eventBus).bind("entity_selected", enableTabs);
    }

    return {
        init: init
        };

}());

$(document).ready(Brooklyn.tabs.init);
