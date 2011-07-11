Brooklyn.sensors = (function() {
    var entity_id;

    function updateTableData(json) {
         var table = $('#sensor-data').dataTable( {
                "bRetrieve": true,
                "bLengthChange": false,
                "bJQueryUI": true,
                "aoColumns": [
                    { "sTitle": "name" },
                    { "sTitle": "description" },
                    { "sTitle": "value" }
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
