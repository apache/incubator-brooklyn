Brooklyn.activity = (function(){
    function ActivityTab() {
        this.id = 'activity';

        this.updateTable = function(json){
            var aoColumns = [ { "mDataProp": "displayName", "sTitle": "Name", "sWidth":"16%" },
                              { "mDataProp": "description", "sTitle": "Description", "sWidth":"17%" },
                              { "mDataProp": "submitTimeUtc", "sTitle": "Submit time", "sWidth":"17%" },
                              { "mDataProp": "startTimeUtc", "sTitle": "Start time", "sWidth":"17%" },
                              { "mDataProp": "endTimeUtc", "sTitle": "End time", "sWidth":"17%" },
                              { "mDataProp": "currentStatus", "sTitle": "Status", "sWidth":"15%" }];

            Brooklyn.util.getDataTable('#activity-data', ".", aoColumns, updateLog, json, undefined);
            $(Brooklyn.eventBus).trigger('update_ok');
        }

        this.update = function() {
            if (typeof this.entity_id !== 'undefined') {
                $.getJSON("../entity/activity?id=" + this.entity_id, this.updateTable).error(
                    function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get activity data.");}
                );
                clearLog();
            }
        }

        this.makeHandlers();
    }

    ActivityTab.prototype = new Brooklyn.tabs.Tab();

    function updateLog(event){
        var settings = Brooklyn.util.getDataTable('#activity-data').fnSettings().aoData;
        for(row in settings) {
            var currentRow = $(settings[row].nTr)
            if(currentRow.hasClass('row_selected')){
                currentRow.removeClass('row_selected');
                break;
            }
        }

        $(event.target.parentNode).addClass('row_selected');

        var result = Brooklyn.util.getDataTableSelectedRowData('#activity-data', event);
        var logBox=document.getElementById("logbox");
        logBox.value=result.detailedStatus;
    }

    function clearLog(){
        var logBox = document.getElementById("logbox");
        logBox.value="";
    }

    function init() {
        var activityTab = new ActivityTab();
        $(Brooklyn.eventBus).bind("entity_selected", activityTab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", activityTab.handler.tabSelected);

        $('#activity').focus(function() {
            Brooklyn.util.pauseUpdate(activityTab);
        });
    }

    return {
        init: init
    };

})();

$(document).ready(Brooklyn.activity.init);

