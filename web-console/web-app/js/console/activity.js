Brooklyn.activity = (function(){

    // Config
    var id = '#activity-data';
    var aoColumns = [ { "mDataProp": "displayName", "sTitle": "Name", "sWidth":"16%" },
                      { "mDataProp": "description", "sTitle": "Description", "sWidth":"17%" },
                      { "mDataProp": "submitTimeUtc", "sTitle": "Submit time", "sWidth":"17%" },
                      { "mDataProp": "startTimeUtc", "sTitle": "Start time", "sWidth":"17%" },
                      { "mDataProp": "endTimeUtc", "sTitle": "End time", "sWidth":"17%" },
                      { "mDataProp": "currentStatus", "sTitle": "Status", "sWidth":"15%" }];

    function updateTable(json){
        Brooklyn.tabs.getDataTable(id, ".", aoColumns, updateLog, json);
    }

    function clearLog(event){
        var logBox = document.getElementById("logbox");
        logBox.value="";
    }

    function updateLog(event){
        var settings = Brooklyn.tabs.getDataTable(id).fnSettings().aoData;
        for(row in settings) {
       		$(settings[row].nTr).removeClass('row_selected');
   		}
 		$(event.target.parentNode).addClass('row_selected');

        var result = Brooklyn.tabs.getDataTableSelectedRowData(id, event);
        if(result) {
            var logBox=document.getElementById("logbox");
            logBox.value=result.statusDetailMultiLine;
        }
    }

    function updateList(e, entity_id) {
        if (entity_id) {
            $.getJSON("activity?id=" + entity_id, updateTable).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get activity data.");}
            );
            clearLog();
        }
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {
        init: init
    };

})();
$(document).ready(Brooklyn.activity.init);

