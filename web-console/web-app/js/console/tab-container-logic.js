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

}
