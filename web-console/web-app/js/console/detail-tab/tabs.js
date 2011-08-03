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

    // NOTE: You can simply call getDataTable(id) once the table is initialized
    function getDataTable(id, sAjaxDataProp, aoColumns, clickCallback, data) {
        var table = $(id).dataTable( {
                "bRetrieve": true, // return existing table if initialized
                "bAutoWidth": false,
                "bLengthChange": false,
                "bJQueryUI": true,
                "bPaginate": false,
                "bStateSave": true,
                "bDeferRender": true,
                "sAjaxDataProp": sAjaxDataProp,
                "aoColumns": aoColumns
        });

        if (clickCallback) {
            $(id + " tbody").click(clickCallback);
        }

        if (data) {
            table.fnClearTable();
            table.fnAddData(data);
        }

        return table;
    }

    function getDataTableSelectedRow(id, event) {
        var table = getDataTable(id);
        var settings = table.fnSettings().aoData;
        return settings[table.fnGetPosition(event.target.parentNode)];
    }

    function getDataTableSelectedRowData(id, event) {
        var row = getDataTableSelectedRow(id, event);
        // TODO bit hacky!
        return row._aData;
    }

    return {
        init: init,
        getDataTable: getDataTable,
        getDataTableSelectedRowData: getDataTableSelectedRowData
    };

}());

$(document).ready(Brooklyn.tabs.init);
