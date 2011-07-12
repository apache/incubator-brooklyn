Brooklyn.activity = (function(){

    // Config
    var id = '#activity-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "name", "sWidth":"100%"  },
                      { "mDataProp": "activitydate", "sTitle": "date", "sWidth":"100%"  }];

    var mydata = [
        {activitydate:"2007-10-01:18:45:30",name:"Effector:Stop,Arg3"},
        {activitydate:"2007-10-02",name:"Effector:Stop,Arg2"},
        {activitydate:"2007-09-01",name:"Effector:Stop,Arg1"},
        {activitydate:"2007-10-04",name:"StatCheck"},
        {activitydate:"2007-10-05",name:"Effector:Stop"},
        {activitydate:"2007-09-06",name:"Effector:Deploy"},
        {activitydate:"2007-10-04",name:"Effector:Start"},
        {activitydate:"2007-10-03:19:01:27",name:"Effector:Reset"},
        {activitydate:"2007-09-01",name:"Effector:Abandon"},
        {activitydate:"2007-10-01",name:"Effector:Stop,Arg3"},
        {activitydate:"2007-10-02",name:"Effector:Stop,Arg2"},
        {activitydate:"2007-09-01",name:"Effector:Stop,Arg1"},
        {activitydate:"2007-10-04",name:"StatCheck"},
        {activitydate:"2007-10-05",name:"Effector:Stop"},
        {activitydate:"2007-09-06",name:"Effector:Deploy"},
        {activitydate:"2007-10-04",name:"Effector:Start"},
        {activitydate:"2007-10-03",name:"Effector:Reset"},
        {activitydate:"2007-09-01",name:"Effector:Abandon"}
    ];

    function createGrid(){
        Brooklyn.tabs.getDataTable(id, ".", aoColumns, updateLog, mydata);
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

    function init() {
        createGrid();
        $('#activity-clear').click(clearLog);
        $('#activity-select').click(selectLog);
    }

    return { init: init, selectLog: selectLog , clearLog: clearLog , updateLog: updateLog }

})();
$(document).ready(Brooklyn.activity.init);

