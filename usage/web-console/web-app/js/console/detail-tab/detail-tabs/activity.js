Brooklyn.activity = (function(){
	var parent;
	
    function ActivityTab() {
        this.id = 'activity';

        parent = this;
        this.updateTable = function(json){
            var aoColumns = [ { "mDataProp": "displayName", "sTitle": "Name", "sWidth":"16%" },
                              { "mDataProp": "description", "sTitle": "Description", "sWidth":"17%" },
                              { "mDataProp": "submitTimeUtc", "sTitle": "Submit time", "sWidth":"17%" },
                              { "mDataProp": "startTimeUtc", "sTitle": "Start time", "sWidth":"17%" },
                              { "mDataProp": "endTimeUtc", "sTitle": "End time", "sWidth":"17%" },
                              { "mDataProp": "currentStatus", "sTitle": "Status", "sWidth":"15%" }];

            Brooklyn.util.getDataTable('#activity-data', ".", aoColumns, updateLog, json, undefined);
            selectTask(selectedTaskId);
            $(Brooklyn.eventBus).trigger('update_ok');
        }

        this.update = function() { updateTasks(); }

        this.makeHandlers();
    }

    function updateTasks() {
        if (typeof parent.entity_id !== 'undefined') {
            $.getJSON("../entity/activity?id=" + parent.entity_id, parent.updateTable).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get activity data.");}
            );
        }    	
    }

    var selectedTaskId;
    
    ActivityTab.prototype = new Brooklyn.tabs.Tab();

    function updateLog(event){
        selectTask(Brooklyn.util.getDataTableSelectedRowData('#activity-data', event).id);
    }
    
    function selectTask(newSelectedTaskId) {
    	selectedTaskId = newSelectedTaskId;
    	
        var settings = Brooklyn.util.getDataTable('#activity-data').fnSettings().aoData;
        var selectedTaskData;
        for(row in settings) {
            var currentRow = $(settings[row].nTr)
            var currentData = settings[row]._aData;
            if (currentData.id == selectedTaskId) {
            	currentRow.addClass('row_selected');
            	selectedTaskData = currentData;
            } else if(currentRow.hasClass('row_selected')){
                currentRow.removeClass('row_selected');
                clearLog();
                break;
            }
        }
    	
        if (selectedTaskData) {
            var logBox=document.getElementById("logbox");        	
            logBox.value = selectedTaskData.detailedStatus;
        }
    }

    function clearLog(){
        var logBox = document.getElementById("logbox");
        logBox.value="";
    }

    function init() {
        var activityTab = new ActivityTab();
        $(Brooklyn.eventBus).bind("entity_selected", activityTab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", activityTab.handler.tabSelected);

        $('#activity-data, #logbox').click(function() {
            Brooklyn.util.pauseUpdate(activityTab);
        });
    }

    return {
        init: init,
        updateTasks: updateTasks
    };

})();

$(document).ready(Brooklyn.activity.init);

