Brooklyn.location = (function() {
    // Config
    var tableId = '#location-data';
    var aoColumns = [ { "mDataProp": "name", "sWidth":"100%"  }];
    var appLocations;
    // Status
    var map;
    var loc;

    function LocationTab() {
        this.id = 'location';

         this.update = function() {
            if (typeof this.entity_id !== 'undefined') {
                $.getJSON("../entity/locations?id=" + this.entity_id, updateLocationsAux).error(
                    function() {$(Brooklyn.eventBus).trigger('update_failed', "Location view could not get locations.");});
            }
        }

        this.makeHandlers();
    }

    LocationTab.prototype = new Brooklyn.tabs.Tab();

    
    function updateLocation(event) {
        reset();
        $(event.target.parentNode).addClass('row_selected');

        var result = Brooklyn.util.getDataTableSelectedRowData(tableId, event);

        result.infowindow.open(map, result.marker);
    }

    function reset() {
        var settings = Brooklyn.util.getDataTable(tableId).fnSettings().aoData;
        for(var row in settings) {
            $(settings[row].nTr).removeClass('row_selected');
            // TODO bit hacky!
            settings[row]._aData.infowindow.close(map, settings[row]._aData.marker);
        }
    }

    function buildAppLocation(loc) {
        var lat = loc.latitude;
        var lon = loc.longitude;
        var name = loc.displayName;

        if (lat != null && lon != null) {
            var gloc = new google.maps.LatLng(lat, lon);
            var marker = new google.maps.Marker({
                map: map,
                position: gloc,
                title: "Title"
            });

            var locationInfo = {"Name": name,
                                "ISO-3166": loc.iso3166,
                                "Lat-Long": displayLatLong(lat, lon),
                                "Address": loc.streetAddress};

            var liw = new Brooklyn.gmaps.ListInfoWindow(locationInfo, map, marker);

            var newLoc = {
                name : name,
                marker : marker,
                infowindow : liw.getInfoWindow(),
                gloc : gloc
            };
            newLoc.locationNumber = appLocations.length;
            appLocations.push(newLoc);
        } else if (loc.iso3166 != null) {
            // use ISO code TBI
            alert("use of ISO Code TBI");
        } else {
            alert("need latitude and longditude (note ISO code TBI)");
        }
    }

    function updateLocationsAux(locations) {
        var myOptions = {
            width: 400,
            mapTypeId: google.maps.MapTypeId.ROADMAP
        }

        map = new google.maps.Map(document.getElementById("map-canvas"), myOptions);
        appLocations = new Array();
        for(var i in locations) {
            buildAppLocation(locations[i]);
        }

        extendMap();

        Brooklyn.util.getDataTable(tableId, '.', aoColumns, updateLocation, appLocations);
    }

    function locationsTabSelected(e, id) {
        reset();

        if (id === 'location') {
            $('#map-canvas').width('98%');
            $('#map-canvas').height('500px');

            extendMap();
        }
    }

    function extendMap() {
        google.maps.event.trigger(map, 'resize');

        if (appLocations.length > 0) {
            var bounds = new google.maps.LatLngBounds(appLocations[0].gloc);
            for (var i in appLocations) {
                bounds.extend(appLocations[i].gloc);
            }
            map.fitBounds(bounds);
        }

        if (appLocations.length == 1) {
            map.setZoom(4);
        }
    }

    function displayLatLong(lat, lon) {
        var displayLatLng =  (!lat || lat > 0 ? '+' : '') + lat + ' ' + (!lon || lon > 0 ? '+' : '') + lon;
        return displayLatLng.replace(/-/g, '&#8209;');
    }

    function init() {
        var locationTab = new LocationTab();
        $(Brooklyn.eventBus).bind("entity_selected", locationTab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", locationTab.handler.tabSelected);
        $(Brooklyn.eventBus).bind("tab_selected", locationsTabSelected);

        $('#location').focus(function() {
            Brooklyn.util.pauseUpdate(locationTab);
        });
    }

    return {
        init : init
    }

})();

$(document).ready(Brooklyn.location.init);
