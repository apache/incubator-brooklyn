Brooklyn.sensors = (function() {
    var parent;
    
    function SensorsTab() {
        this.id = 'sensors';

        parent = this;
        this.update = function() { updateSensors(); }

        this.updateTableData = function(json) {
            for (i in json) {
              json[i].nameWithToolTip = '<div title="'+json[i].description+'">'+json[i].name+'</div>';
              var actions = json[i].actions;
              json[i].actionHtml = '';
              for (ai in actions) {
                json[i].actionHtml = json[i].actionHtml + ' <b><a href="'+
                  actions[ai].url+'" target="_blank">'+
                  actions[ai].name+'</a></b> &nbsp; ';
              }
              if (typeof parent.entity_id !== 'undefined') {
                json[i].actionHtml = json[i].actionHtml + ' <a href="'+
                  '../entity/sensor?entityId='+parent.entity_id+'&sensorId='+json[i].name+'" target="_blank">JSON</a> ';
              }
              // others, e.g. little graphs
            }
            
            var table = Brooklyn.util.getDataTable('#sensor-data');
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

    function updateSensors() {
        if (typeof parent.entity_id !== 'undefined') {
            $.getJSON("../entity/sensors?id=" + parent.entity_id, parent.updateTableData).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get sensor data.");}
            );
        }
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
        updateSensors: updateSensors,
        toggleShowEmptySensors: toggleShowEmptySensors,
        setSensorEmptyFilter: setShowEmptySensors
    };

})();

$(document).ready(Brooklyn.sensors.init);

