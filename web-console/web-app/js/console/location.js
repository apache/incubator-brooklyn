Brooklyn.location = (function() {
    var map;
    var loc;

    function init() {
        var address = "Crichton Street, Edinburgh, UK";
        geocoder = new google.maps.Geocoder();
        var myOptions = {
          zoom: 8,
          mapTypeId: google.maps.MapTypeId.ROADMAP
        }
        map = new google.maps.Map(document.getElementById("map-canvas"), myOptions);

        geocoder.geocode( { 'address': address}, function(results, status) {
          if (status == google.maps.GeocoderStatus.OK) {
            loc = results[0].geometry.location;
            var marker = new google.maps.Marker({
                map: map,
                position: loc
            });
          } else {
            alert("Geocode was not successful for the following reason: " + status);
          }
        });
        $(Brooklyn.eventBus).bind("tab_selected", resize);
    }

    function resize(e, id) {
        if (id == 'location') {
            $('#map-canvas').width('98%');
            $('#map-canvas').height('500px');
            google.maps.event.trigger(map, 'resize');
            map.setCenter(loc);
        }
    }

    return { init : init, resize : resize }
})();

$(document).ready(Brooklyn.location.init);

