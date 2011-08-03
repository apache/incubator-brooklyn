Brooklyn.sensors = (function() {
    var id = 'sensors';

    // Config
    var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"30%"  },
                      { "mDataProp": "description", "sTitle": "description", "sWidth":"30%" },
                      { "mDataProp": "value", "sTitle": "value", "sWidth":"20%", "bSortable": false },
                      { "mDataProp": "timestamp", "sTitle": "last updated", "sWidth":"20%"}];

    // State
    var entity_id;

    function updateTableData(json) {
        Brooklyn.tabs.getDataTable('#sensor-data', ".", aoColumns, undefined, json);
        $(Brooklyn.eventBus).trigger('update_ok');
    }

    function update() {
        if (typeof entity_id !== 'undefined') {
            $.getJSON("../entity/sensors?id=" + entity_id, updateTableData).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get sensor data.");}
            );
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
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.sensors.init);
