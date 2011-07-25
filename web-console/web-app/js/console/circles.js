Brooklyn.location = (function() {
    var map;

    function drawCircles() {
        var lat = 55;
        var lng = -2.5;

        var circle_latlong = new google.maps.LatLng(lat, lng);
        var circle_options = {
            map: map,
            center: circle_latlong,
            clickableboolean: false,
            fillColor: "#FF0000",
            fillOpacity: 0.4,
            radius: 50000, // meters
            strokeColor: "#FF0000",
            strokeOpacity: 1,
            strokeWeight: 1,
            zIndex: 1
        };

        var circle = new google.maps.Circle(circle_options);
    }

  function drawMap() {
    var latlng = new google.maps.LatLng(55.6, -2.5);
    var myOptions = {
      zoom: 2,
      center: latlng,
      mapTypeId: google.maps.MapTypeId.ROADMAP
    };
      map = new google.maps.Map(document.getElementById("map_canvas"),
                                myOptions);
  }

    function init() {
        drawMap();
        drawCircles();
    }

    return {init: init};
})();

$(document).ready(Brooklyn.location.init);
