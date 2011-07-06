Brooklyn.sensors = (function() {
    var entity_id;

    /* Fetch sensor readings for an entity and show them in an html table. */
    function drawSensorData(json) {
        var table = "<table><thead><tr><th>Sensor</th><th>Description</th><th>Value</th></tr></thead>\n";
        table += "<tbody>\n";

        for (name in json) {
            table += "<tr> <td> " + name + " </td> <td>" + json[name].description + " </td> <td>" + json[name].value + "</td> </tr>\n";
        }

        table += "</tbody></table>";

        $("#sensor-data").html(table);
    }

    /* Make a GET request for sensor data for an entity of the given id. Then call
     * drawSensorData on the returned json.
     */
    function getAndDrawSensorData(id) {
        // TODO: Handle failure
        $.getJSON("sensors?id=" + entity_id, drawSensorData);
    }

    function update() {
        if (typeof entity_id === 'undefined') {
            return;
        }
        getAndDrawSensorData(entity_id);
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
        $(Brooklyn.eventBus).bind("update", getAndDrawSensorData);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.sensors.init);
