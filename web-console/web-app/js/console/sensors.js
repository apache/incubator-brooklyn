Brooklyn.sensors = (function() {
    // Config
    var id = '#sensor-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"30%"  },
                      { "mDataProp": "description", "sTitle": "description", "sWidth":"50%" },
                      { "mDataProp": "value", "sTitle": "value", "sWidth":"20%", "bSortable": false  }];

    // State
    var entity_id;

    function updateTableData(json) {
        Brooklyn.tabs.getDataTable(id, ".", aoColumns, undefined, json);
    }

    function update() {
        if (entity_id) {
            $.getJSON("sensors?id=" + entity_id, updateTableData);
            $(Brooklyn.eventBus).trigger('update_ok');
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

$(document).ready(Brooklyn.sensors.init);
