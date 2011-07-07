Brooklyn.sensors = (function() {
    var entity_id;
    var x;

    /* Make a GET request for sensor data for an entity of the given id. Then call
     * drawSensorData on the returned json.
     */
    function getAndDrawSensorData(entity_id) {
          $("#sensor-data").jqGrid({
                url:'sensors?id=' + entity_id,
                datatype: "json",
    	        height: '100%',
                width: '700px',
                colNames:['Name', 'Description', 'Value'],
                colModel:[
                    {name:'name',index:'name', jsonmap:'name', width:200},
                    {name:'description',index:'description', width:250},
                    {name:'value',index:'value',sortable:false}
                ],
                rowNum:10,
                sortname: 'name',
                viewrecords: true,
                sortorder: "desc",
                multiselect: false,
                jsonReader: {
		            repeatitems : false,
		            id: "0"
            	}
            });
    }

    function update() {
        if (typeof entity_id === 'undefined') {
            return;
        }
        if (typeof x == 'undefined') {
            getAndDrawSensorData(entity_id);
            x = 1;
        } else {
    	    $("#sensor-data").jqGrid('setGridParam',{url:"sensors?id=" + entity_id}).trigger("reloadGrid");
    	}
    	$(Brooklyn.eventBus).trigger('update_ok');
    }

    /* This method is intended to be called as an event handler. The e paramater is
     * unused.
     */
    function setEntityIdAndUpdate(e, id) {
        entity_id = id;
        update();
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", setEntityIdAndUpdate);
        $(Brooklyn.eventBus).bind("update", update);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.sensors.init);
