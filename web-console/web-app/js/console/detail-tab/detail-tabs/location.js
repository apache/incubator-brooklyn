Brooklyn.location = (function() {
    // Config
    var tableId = '#location-data';
    var aoColumns = [ { "mDataProp": "name", "sTitle": "Location", "sWidth":"100%"  }];
    var appLocations;
    // Status
    var map;
    var loc;
    var locationNumber = 0;

    function updateLocation(event) {
        var result = Brooklyn.tabs.getDataTableSelectedRowData(tableId, event);

        // TODO why is this necessary? (location, etc should be in result!)
        for (i in appLocations) {
            if (appLocations[i].name === result.name) {
                locationNumber = i;
                moveToLoc();
                break;
            }
        }
 	$(event.target.parentNode).addClass('row_selected');
    }

    function moveToLoc() {
        var settings = Brooklyn.tabs.getDataTable(tableId).fnSettings().aoData;
        for(row in settings) {
       	    $(settings[row].nTr).removeClass('row_selected');
   	}

        for(i in appLocations) {
            appLocations[i].infowindow.close(map , appLocations[i].marker);
        }

        map.setCenter(appLocations[locationNumber].location);
        appLocations[locationNumber].infowindow.open(map , appLocations[locationNumber].marker);
    }

    function addLocationToMap(location, i){
        var address = location.add;
        var lat = location.lat;
        var lon = location.lon;
        var name = location.name;
        var iso = location.iso;
        var description = location.description;

        if (address != null) {
            new google.maps.Geocoder().geocode( { 'address': address}, function(results, status) {
                if (status === google.maps.GeocoderStatus.OK) { 
                    loc = results[0].geometry.location;
                    appLocations[i].location = loc;
                    var contentString = '<div id="content" style="height:80px">'+
                        '<h1>'+address+'</h1>'+
                        '<table border="1">'+
                        '<tr>'+
                        '<td>Address</td>'+
                        '<td>Active</td>'+
                        '<td>Resources</td>'+
                        '</tr>'+
                        '<tr>'+
                        '<td>'+address+'</td>'+
                        '<td>True</td>'+
                        '<td>'+'resources'+'</td>'+
                        '</tr>'+
                        '</table>'+
                        '</div>';
                    var marker = new google.maps.Marker({
                        map: map,
                        position: loc,
                        title: address
                    });
                    var infowindow = new google.maps.InfoWindow({
                        content: contentString
                    });
                    google.maps.event.addListener(marker, 'click' , function(){
                        infowindow.open(map, marker);
                    });
                    appLocations[i].marker = marker;
                    appLocations[i].infowindow = infowindow;
                    appLocations[i].locationNumber = i;
                    locationNumber = locationNumber + 1;
                    map.setCenter(loc);
                } else {
                    $(Brooklyn.eventBus).trigger('update_failed', "Got data from server but could not geocode an entity: "
                                                 + status);
                }
            });
        } else if (lat != null && lon != null) {
            loc = new google.maps.LatLng(lat, lon);
            appLocations[i].location = loc;
            var contentString = '<div id="content" style="height:80px">'+
                '<h1>'+'address'+'</h1>'+
                '<table border="1">'+
                '<tr>'+
                '<td>Address</td>'+
                '<td>Active</td>'+
                '<td>Resources</td>'+
                '</tr>'+
                '<tr>'+
                '<td>'+'address'+'</td>'+
                '<td>True</td>'+
                '<td>'+'resources'+'</td>'+
                '</tr>'+
                '</table>'+
                '</div>';
            var marker = new google.maps.Marker({
                map: map,
                position: loc ,
                title: "Title"
            });
            var infowindow = new google.maps.InfoWindow({
                content: contentString
            });
            google.maps.event.addListener(marker, 'click' , function(){
                infowindow.open(map , marker);
            });
            appLocations[i].marker = marker;
            appLocations[i].infowindow = infowindow;
            appLocations[i].locationNumber = i;
            locationNumber = locationNumber + 1;
            map.setCenter(loc);
        } else if (iso != null) {
            // use ISO code TBI
            alert("use of ISO Code TBI");
        } else {
            $(Brooklyn.eventBus).trigger('update_failed',
                                         "No geolocation information available from google for " + location.name);
        }
    }

    // TODO call when set of locations changes?
    function updateLocations() {
        var myOptions = {
            width: 400,
            zoom: 7,
            mapTypeId: google.maps.MapTypeId.ROADMAP
        }

        map = new google.maps.Map(document.getElementById("map-canvas"), myOptions);
        for(i in appLocations) {
            addLocationToMap(appLocations[i], i);
        }
        Brooklyn.util.getDataTable(tableId, '.', aoColumns, updateLocation, appLocations, false);
    }

    function resize(e, id) {
        if (id === 'location') {
            $('#map-canvas').width('98%');
            $('#map-canvas').height('500px');

            google.maps.event.trigger(map, 'resize');
            if(appLocations.length <= locationNumber) {
                locationNumber = 0;
            }
            map.setCenter(appLocations[locationNumber].location);
        }
    }
    function getLocations(e,id){
        if (typeof id !== 'undefined') {
            $.getJSON("../entity/locations?id=" + id, handleLocations).error(
                function() {$(Brooklyn.eventBus).trigger('update_failed', "Location view could not get locations.");});
        }
    }

    function handleLocations(locations){
        appLocations = new Array();
        if (locations.length > 0) {
            for(i in locations){
                var description = locations[i].description;
                var displayname = locations[i].displayName;
                var name = locations[i].displayName;
                var lat = locations[i].latitude;
                var lon = locations[i].longitude;
                var add = locations[i].streetAddress;
                var iso = locations[i].iso;
                var jsonLoc = {name: name,
                               displayname: displayname,
                               description: description,
                               lat: lat,
                               lon: lon,
                               add: add,
                               iso: iso};

                appLocations.push(jsonLoc);
            }
            updateLocations();
        } else {
            updateLocations();
        }
    }
    

    function init() {
        $(Brooklyn.eventBus).bind("tab_selected", resize);
        $(Brooklyn.eventBus).bind("entity_selected", getLocations);
    }

    return { init : init, resize : resize, locationNumber: locationNumber, appLocations: appLocations }
})();

$(document).ready(Brooklyn.location.init);
