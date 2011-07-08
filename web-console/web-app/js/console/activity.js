Brooklyn.activity = (function(){
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
        $("#activitylist").jqGrid({
            datatype: "local",
            height: 250,
            width:290,
            colNames:['Name', 'Date'],
            colModel:[
            {name:'name',index:'name'},
            {name:'activitydate',index:'invdate'}
            ],
            multiselect: false,
            onSelectRow: function(id){updateLog(id);}
        });
    }

    function populateGrid(){
        for(i=0;i<mydata.length;i++){
            $("#activitylist").jqGrid('addRowData',i+1,mydata[i]);}
    }

    function selectLog(){
        document.getElementById("logbox").select();
    }

    function clearLog(){
        var logBox = document.getElementById("logbox");
        logBox.value="";
    }

    function updateLog(name){
        var id = name;
        if(id){
            var result = jQuery("#activitylist").jqGrid('getRowData',id);
            var logBox=document.getElementById("logbox");
            logBox.value+="######### NEW LOG ##########"+
            "--- Console Log Output for "+result.name+" ---"+
            " activity last initiated on "+result.activitydate+
            " start of process status checked "+
            "log,log,log";
        }
    }

    return{ createGrid : createGrid , populateGrid : populateGrid , selectLog : selectLog , clearLog : clearLog , updateLog : updateLog }
})();
$(document).ready(function(){
    Brooklyn.activity.createGrid();
    Brooklyn.activity.populateGrid();
});

