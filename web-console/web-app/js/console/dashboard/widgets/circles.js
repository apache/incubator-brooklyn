Brooklyn.circles = (function() {
    var map;
    var locationMarkers = {};

    // meters squared per entity
    var area_per_entity = 300000000000;

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

    /* Returns the area in square meters that a circle should be to represent
     * count entities at a location. */
    function location_area(count) {
        return area_per_entity * count;
    }

    /* Returns the radius of a circle of the given area. */
    function radius(area) {
        return Math.sqrt(area / Math.PI);
    }

    function drawCirclesFromJSON(json) {
        var newLocs = {};
        var id;
        var lm;

        for (id in json) {
            var l = json[id];
            if (lm = locationMarkers[id]) {
                console.log("Updating " + id);
                // Update

                var latlng = new google.maps.LatLng(l.lat, l.lng);

                lm.circle.setRadius(radius(location_area(l.entity_count)));
                lm.circle.setCenter(latlng);

                lm.marker.setPosition(latlng);

                lm.infoWindow.setPairs(l);

                newLocs[id] = lm;
            } else {
                // Add
                console.log("Adding " + id);
                var circle = drawCircle(l.lat, l.lng, radius(location_area(l.entity_count)));

                var marker = new google.maps.Marker({
                    map: map,
                    position: new google.maps.LatLng(l.lat, l.lng),
                    title: "Bleh",
                });


                var locationInfo = {name: "bob"};
                var infoWindow = new Brooklyn.gmaps.ListInfoWindow(locationInfo, map, marker);

                circle.bindTo('center', marker, 'position');

                newLocs[id] = {circle: circle,
                               marker: marker,
                               infoWindow: infoWindow};
            }
        }

        for (id in locationMarkers) {
            if (! newLocs[id]) {
                // location has been removed
                console.log("Deleting " + id);
                var lm = locationMarkers[id];
                lm.circle.setMap(null);
                lm.marker.setMap(null);
                lm.infoWindow.getInfoWindow().setMap(null);
            }
        }
        locationMarkers = newLocs;
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
        drawCircles();
        $(Brooklyn.eventBus).bind("update", update);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.circles.init);
