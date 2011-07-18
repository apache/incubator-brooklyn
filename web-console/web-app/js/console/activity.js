Brooklyn.activity = (function(){

    // Config
    var id = '#activity-data';
    var aoColumns = [ { "mDataProp": "submitTimeUtc", "sTitle": "Submit time", "sWidth":"10%"  },
                      { "mDataProp": "statusDetail", "sTitle": "Status", "sWidth":"90%"  }];

    function updateTable(json){
        Brooklyn.tabs.getDataTable(id, ".", aoColumns, updateLog, json);
    }

    function selectLog(event){
        document.getElementById("logbox").select();
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
            logBox.value+="######### NEW LOG ##########"+
            "--- Console Log Output for "+result.name+" ---"+
            " activity last initiated on "+result.activitydate+
            " start of process status checked "+
            "log,log,log";
        }
    }

    function updateList(e, entity_id) {
        if (entity_id) {
             $.getJSON("activity?id=" + entity_id, updateTable).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get activity data.");}
            );
        }
    }

    function init() {
        $('#activity-clear').click(clearLog);
        $('#activity-select').click(selectLog);
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {
        init: init,
        selectLog: selectLog,
        clearLog: clearLog,
        updateLog: updateLog
    };

})();
$(document).ready(Brooklyn.activity.init);

