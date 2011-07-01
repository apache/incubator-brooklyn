function drawSensorData(json) {
    var table = "<table> <thead> <tr> <th>Sensor</th> <th>Value</th> </tr> </thead>\n";
    table += "<tbody>\n";

    for (name in json) {
        var line = "<tr> <td> " + name + " </td> <td> " + json[name] + "</td> </tr>\n";
        table += line;
    }

    $("#sensors").html(table);
}

function initTabs() {
    $("#sensors").html("Oh what lovely sensor data I see.");

    $("#tabs").tabs({
        show: function (event, ui) {
            setAccordionLayout(ui);
        }
    });

    $("#summary").accordion({fillSpace: true});

    var url = "sensors";
    $.getJSON(url, drawSensorData);
}

function setAccordionLayout(accordion) {
   $("#summary").css('padding', '0px');
}
