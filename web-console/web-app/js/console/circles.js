Brooklyn.location = (function() {
    var map;
    var circles = [];

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

    function hardcodedCircleDrawer() {
        drawCircle(56, -2, 100000);
    }

    function drawCircles() {
        $.getJSON("circles", drawCirclesFromJSON).error(
            function() {$(Brooklyn.eventBus).trigger('update_failed', "Could not get location size data for circle drawing.");}
        );
    }

    function drawCirclesFromJSON(json) {
        // Remove all existing circles
        for (c in circles) {
            c.setMap(null);
        }

        // Draw the new ones
        for (location in json) {
            circles.push(drawCircle(location.lat, location.long, location.radius));
        }
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
        hardcodedCircleDrawer();
        $(Brooklyn.eventBus).bind("update", update);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.location.init);
