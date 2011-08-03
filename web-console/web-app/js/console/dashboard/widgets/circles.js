Brooklyn.circles = (function() {
    var map;
    var circles = [];

    // meters radius per entity at each location
    var circle_size = 50000;

    function drawCircle(lat, lng, radius) {
        var circle_latlong = new google.maps.LatLng(lat, lng);
        var circle_options = {
            map: map,
            center: circle_latlong,
            clickableboolean: false,
            fillColor: "#FF0000",
            fillOpacity: 0.4,
            radius: radius, // meters
            strokeColor: "#FF0000",
            strokeOpacity: 1,
            strokeWeight: 1,
            zIndex: 1
        };

        return new google.maps.Circle(circle_options);
    }

    function drawCirclesFromJSON(json) {
        // Remove all existing circles
        for (i in circles) {
            var c = circles[i];
            c.setMap(null);
        }

        // Draw the new ones
        for (var i in json) {
            var l = json[i];
            circles.push(drawCircle(l.lat, l.lng, l.entity_count * circle_size));
        }
    }

    function drawCircles() {
        $.getJSON("../entity/circles", drawCirclesFromJSON).error(
            function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get location size data for circle drawing.");}
        );
    }

    function drawMap() {
        var latlng = new google.maps.LatLng(55.6, -2.5);
        var myOptions = {
            zoom: 2,
            center: latlng,
            mapTypeId: google.maps.MapTypeId.ROADMAP
        };
        map = new google.maps.Map(document.getElementById("circles-map"),
                                  myOptions);
    }

    function update() {
        drawCircles();
    }

    function init() {
        drawMap();
        $(Brooklyn.eventBus).bind("update", update);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.circles.init);
