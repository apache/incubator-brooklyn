function enableTabs() {
    $("#tabs").tabs("option", "disabled", false);
}

function disableTabs() {
    // Should be able to use true here instead of list but it was not working.
     // Must extend the list to the number of tabs used.
    $("#tabs").tabs("option", "disabled", [0,1,2,3,4,5,6,7,8,9,10]);
}

function initTabs() {
    $("#tabs").tabs();
    disableTabs();

    var selectEntityMessage = "<p>Select an entity in the tree to the left to work with it here.</p>";
    $('#summary').html(selectEntityMessage);

    $(eventBus).bind("entity_selected", enableTabs);
    $(eventBus).bind("entity_selected", getAndDrawSensorData);
}

/* Fetch sensor readings for an entity and show them in an html table. */
function drawSensorData(json) {
    var table = "<table> <thead> <tr> <th>Sensor</th> <th>Value</th> </tr> </thead>\n";
    table += "<tbody>\n";

    for (name in json) {
        var line = "<tr> <td> " + name + " </td> <td> " + json[name] + "</td> </tr>\n";
        table += line;
    }
    table += "</tbody></table>";

    $("#sensor-data").html(table);
}

/* Make a GET request for sensor data for an entity of the given id. Then call
 * drawSensorData on the returned json.
 *
* This method is intended to be called as an event handler. The e paramater is
* unused.
*/
function getAndDrawSensorData(e, entity_id) {
    var baseUrl = "sensors";

    var url = baseUrl + "?id=" + entity_id;
    // TODO: Handle failure
    $.getJSON(url, drawSensorData);
}
