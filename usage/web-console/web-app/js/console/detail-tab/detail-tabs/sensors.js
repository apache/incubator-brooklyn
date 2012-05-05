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
            for (i in json) {
              json[i].actionHtml = '<a href="www.google.com" target="_new">Open</a>';
              json[i].nameWithToolTip = '<div title="'+json[i].description+'">'+json[i].name+'</div>';
            }
            
            console.log("sensors update"); 
            console.log(json);
            var table = Brooklyn.util.getDataTable('#sensor-data');
            console.log(table);
            table.fnClearTable(false);
            table.fnAddData(json);
            
            $(Brooklyn.eventBus).trigger('update_ok');
        }

        this.makeHandlers();
    }
    
    SensorsTab.prototype = new Brooklyn.tabs.Tab();

    function init() {
        var tab = new SensorsTab();
        $(Brooklyn.eventBus).bind("entity_selected", tab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", tab.handler.tabSelected);
        
        $('#sensor-data').click(function() {
            Brooklyn.util.pauseUpdate(tab);
        });
        
        // Config
        var aoColumns = [ { "mDataProp": "nameWithToolTip", "sTitle": "Key", "sWidth":"25%"  },
                          { "mDataProp": "value", "sTitle": "Value", "sWidth":"40%", "bSortable": false },
                          { "mDataProp": "actionHtml", "sTitle": "Actions", "sWidth":"40%", "bSortable": false },
                      ];
        var table = Brooklyn.util.getDataTable('#sensor-data', ".", aoColumns, undefined, undefined, false);
        table.fnFilter( '.+', 1, true );
    }

    var showEmptySensors = false;
    function toggleShowEmptySensors() {
      setShowEmptySensors(!showEmptySensors)
    }
    function setShowEmptySensors(filter) {
      showEmptySensors = filter;
      var table = Brooklyn.util.getDataTable('#sensor-data');
      if (filter) table.fnFilter( '.*', 1, true );
      else table.fnFilter( '.+', 1, true );
    }

    return {
        init: init,
        toggleShowEmptySensors: toggleShowEmptySensors,
        setSensorEmptyFilter: setShowEmptySensors
    };

})();

$(document).ready(Brooklyn.sensors.init);

