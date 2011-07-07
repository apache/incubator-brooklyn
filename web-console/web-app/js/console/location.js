var map;
var loc;
var locations = new Array();
var locationNumber = 0;

Brooklyn.location = (function() {
    
    function init() {
        var edinburgh = "Edinburgh, UK";
        var japan = "Tokyo, Japan";
        var newyork = "New York, USA";
        var london = "London, UK";
        geocoder = new google.maps.Geocoder();
        var myOptions = {
          zoom: 7,
          mapTypeId: google.maps.MapTypeId.ROADMAP
        }
        map = new google.maps.Map(document.getElementById("map-canvas"), myOptions);
        addLocation(london);
        addLocation(newyork);
        addLocation(japan);
        addLocation(edinburgh);
        locationNumber = locationNumber - 1;
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

    function addLocation(address){
        geocoder.geocode( { 'address': address}, function(results, status) {
            if (status == google.maps.GeocoderStatus.OK) {
                loc = results[0].geometry.location;
                loctext = results[0].formatted_address;
                var contentString = '<div id="content">'+
                '<h1>'+loctext+'</h1>'+
                '<table border="1">'+
                    '<tr>'+
                        '<td>Location Name</td>'+
                        '<td>Active</td>'+
                        '<td>Resources</td>'+
                    '</tr>'+
                    '<tr>'+
                        '<td>'+loctext+'</td>'+
                        '<td>True</td>'+
                        '<td>600</td>'+
                    '</tr>'+
                '</table>'+
                'Could possibly put a jQuery grid in here to display data about the location etc.'+
                '</div>';
                var marker = new google.maps.Marker({
                    map: map,
                    position: loc ,
                    title: loctext
                });
                var infoWindow = new google.maps.InfoWindow({
                    content: contentString
                });
                google.maps.event.addListener(marker, 'click' , function(){
                    infoWindow.open(map , marker);
                });
                locations.push(loc);
                locationNumber = locationNumber + 1;
            } else {
                alert("Geocode was not successful for the following reason: " + status);
            }
        });
    }
    return { init : init, resize : resize}
})();

$(document).ready(Brooklyn.location.init);

//Other Map Functions, Toggle etc.

function toggleLocation(){
    if(locationNumber==(locations.length-1)){
        locationNumber = 0;
    }
    else{
        locationNumber = locationNumber + 1;
    }
    map.setCenter(locations[locationNumber]);
}

