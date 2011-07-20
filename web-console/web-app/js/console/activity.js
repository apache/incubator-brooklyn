Brooklyn.activity = (function(){

    // Config
    var id = '#activity-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "Name", "sWidth":"10%" },
                      { "mDataProp": "description", "sTitle": "Description", "sWidth":"10%" },
                      { "mDataProp": "submitTimeUtc", "sTitle": "Submit time", "sWidth":"10%" },
                      { "mDataProp": "startTimeUtc", "sTitle": "Start time", "sWidth":"10%" },
                      { "mDataProp": "endTimeUtc", "sTitle": "End time", "sWidth":"10%" },
                      { "mDataProp": "statusDetail", "sTitle": "Status", "sWidth":"10%" },
                      { "mDataProp": "submitted", "sTitle": "Submitted", "sWidth":"10%" },
                      { "mDataProp": "done", "sTitle": "Done", "sWidth":"10%" },
                      { "mDataProp": "error", "sTitle": "Error", "sWidth":"10%" },
                      { "mDataProp": "cancelled", "sTitle": "Cancelled", "sWidth":"10%" }];

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
            logBox.value=result.statusDetailMultiLine;
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
        init: init
    };

})();
$(document).ready(Brooklyn.activity.init);

