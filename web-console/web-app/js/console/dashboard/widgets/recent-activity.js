Brooklyn.activitywidget = ( function(){
//Code for the recent activity widget.

/* INITIAL CONFIG */
    var id = "#recent-activity-table";
    var aoColumns = [   { "mDataProp": "entityDisplayName", "sTitle": "Entity Name", "sWidth":"20%" },
                        { "mDataProp": "displayName", "sTitle": "Task Name", "sWidth":"20%" },
                        { "mDataProp": "submitTimeUtc", "sTitle": "Submit time", "sWidth":"20%" },
                        { "mDataProp": "endTimeUtc", "sTitle": "End time", "sWidth":"20%" },
                        { "mDataProp": "currentStatus", "sTitle": "Status", "sWidth":"20%" }];
    
    function updateWidgetTable(json){
        Brooklyn.util.getDataTable(id, ".", aoColumns, updateLog, json, true);
    }

    function updateLog(event){
        //alert("Logs up to date");
        //could do some sort of pop up with the table data maybe?
    }


    function init(){
        //call a service to get the JSON and then update the table
        //For now it's started.
        //alert("recent activity obtained");
        getRecentActivity();
    }

    function getRecentActivity(){
        $.getJSON("../entity/allActivity", updateWidgetTable).error(
            function(){$(Brooklyn.eventBus).trigger('update_failed', 'Could not obtain recent activity');}
        );
    }

    return {
        init: init
    };


})();
$(document).ready( Brooklyn.activitywidget.init );
