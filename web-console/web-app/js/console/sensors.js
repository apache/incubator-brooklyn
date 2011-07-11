Brooklyn.sensors = (function() {
    var entity_id;

    function updateTableData(json) {
         var table = $('#sensor-data').dataTable( {
                "bRetrieve": true,
                "bAutoWidth": false,
                "bLengthChange": false,
                "bJQueryUI": true,
                "bPaginate": false,
                "aoColumns": [
                    { "sTitle": "name", "sWidth":"30%"  },
                    { "sTitle": "description", "sWidth":"50%" },
                    { "sTitle": "value", "sWidth":"20%"  }
                ]
        });
        table.fnClearTable();
        table.fnAddData(json);
    }

    function update() {
        if (typeof entity_id === 'undefined') {
            return;
        }
        $.getJSON("sensors?id=" + entity_id, updateTableData);
        $(Brooklyn.eventBus).trigger('update_ok');
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
