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

function initTabs() {
    $("#tabs").tabs({
        show: function (event, ui) {
            var url = "sensors";
            $.getJSON(url, drawSensorData);
        }
    });

    $(eventBus).bind("entity_selected", getAndDrawSensorData);
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

function setAccordionLayout(accordion) {
   $("#summary").css('padding', '0px');
}
