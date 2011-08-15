Brooklyn.sensors = (function() {
    function SensorsTab() {
        this.id = 'sensors';

        this.update = function() {
            if (typeof this.entity_id !== 'undefined') {
                $.getJSON("../entity/sensors?id=" + this.entity_id, this.updateTableData).error(
                    function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get sensor data.");}
                );
            }
        };

        this.updateTableData = function(json) {
            // Config
            var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"30%"  },
                              { "mDataProp": "description", "sTitle": "description", "sWidth":"30%" },
                              { "mDataProp": "value", "sTitle": "value", "sWidth":"20%", "bSortable": false },
                              { "mDataProp": "timestamp", "sTitle": "last updated", "sWidth":"20%"}];

            Brooklyn.util.getDataTable('#sensor-data', ".", aoColumns, undefined, json, false);
            $(Brooklyn.eventBus).trigger('update_ok');
        }

        this.makeHandlers();
    }
    SensorsTab.prototype = new Brooklyn.tabs.Tab();

    function init() {
        var tab = new SensorsTab();
        $(Brooklyn.eventBus).bind("entity_selected", tab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", tab.handler.tabSelected);

        $('#sensors').focus(function() {
            Brooklyn.util.pauseUpdate(tab);
        });
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.sensors.init);
