Brooklyn.location = (function() {
    var map;
    var loc;
    var locationNumber = 0;
    var markers = new Array();
    var infowindows = new Array();
    var appLocations = { "locations" : [ 
        {   "locationname" : "London, UK" ,
            "locationresources" : "60" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Edinburgh, UK" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Tokyo, Japan" ,
            "locationresources" : "500" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "New York, USA" ,
            "locationresources" : "400" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "California, USA" ,
            "locationresources" : "600" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Hertfordshire, UK" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Silicon Valley, USA" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Hong Kong, China" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Shanghai, China" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Brooklyn, USA" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"},
        {   "locationname" : "Berlin, Germany" ,
            "locationresources" : "25" ,
            "locationpoint" : "google Calculated"}
    ]};

    function init() {
        createMap();
        createLocationsGrid();
        addLocationsToWidgets();
        locationNumber = locationNumber - 1;
        $(Brooklyn.eventBus).bind("tab_selected", resize);
    }
    function createLocationsGrid(){
        $("#locationlist").jqGrid({
            datatype: "local",
            height: 500,
            width:200,
            colNames:['Name'],
            colModel:[
            {name:'locationname',index:'locationname'},
            ],
            multiselect: false,
            onSelectRow: function(id){updateLocation(id);}
        });
    }
    function populateLocationsGrid(){
        for(i=0;i<appLocations.locations.length;i++){
            $("#locationlist").jqGrid('addRowData',i+1,appLocations.locations[i]);}
    }
    function updateLocation(location){
        var id = location;
        if(id){
            var result = jQuery("#locationlist").jqGrid('getRowData',id);
            //get the point for the address
            geocoder.geocode( { 'address': result.locationname}, function(results, status) {
            if (status == google.maps.GeocoderStatus.OK) {
                loc = results[0].geometry.location;
                map.setCenter(loc);
                for(i=0;i<appLocations.locations.length;i++){
                    if(appLocations.locations[i].locationname == result.locationname){
                    // in here set the window and marker to be open.
                    var locNumber = appLocations.locations[i].locationNumber;
                    locwindow = infowindows[locNumber];
                    locmarker = markers[locNumber];
                    locwindow.open(map,locmarker);
                    //alert(id);     = 0 for London but actually 1.
                    }
                }
                } else {
                alert("Geocode was not successful for the following reason: " + status);
            }
            });
            }
    }
    function createMap(){
        geocoder = new google.maps.Geocoder();
        var myOptions = {
            width:400,
            zoom: 7,
            mapTypeId: google.maps.MapTypeId.ROADMAP
        }
        map = new google.maps.Map(document.getElementById("map-canvas"), myOptions);
    }
    function addLocationsToWidgets(){
    for(i=0;i<appLocations.locations.length;i++){
            addLocationToMap(appLocations.locations[i].locationname ,
                        appLocations.locations[i].locationresources ,
                        i);
            addLocationToGrid(i+1,appLocations.locations[i]);
            }
    }
    function addLocationToGrid(row,location){
        $("#locationlist").jqGrid('addRowData',row,location);
    }
    function resize(e, id) {
        if (id == 'location') {
            $('#map-canvas').width('98%');
            $('#map-canvas').height('500px');
            google.maps.event.trigger(map, 'resize');
            map.setCenter(loc);
        }
    }
    function addLocationToMap(address , resources , i){
        geocoder.geocode( { 'address': address}, function(results, status) {
            if (status == google.maps.GeocoderStatus.OK) {
                loctext = results[0].formatted_address;
                loc = results[0].geometry.location;
                appLocations.locations[i].locationpoint = loc;
                var contentString = '<div id="content" style="height:200px">'+
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
                        '<td>'+resources+'</td>'+
                    '</tr>'+
                '</table>'+
                '</div>';
                var marker = new google.maps.Marker({
                    map: map,
                    position: loc ,
                    title: loctext
                });
                var infowindow = new google.maps.InfoWindow({
                    content: contentString
                });
                google.maps.event.addListener(marker, 'click' , function(){
                    infowindow.open(map , marker);
                });
                markers.push(marker);
                infowindows.push(infowindow);
                appLocations.locations[i].locationNumber = i;
                locationNumber = locationNumber + 1;
            } else {
                alert("Geocode was not successful for the following reason: " + status);
            }
        });
    }

    function toggleLocation(){
        if(locationNumber==(appLocations.locations.length-1)){
            locationNumber = 0;
        }
        else{
            locationNumber = locationNumber + 1;
        }
        map.setCenter(appLocations.locations[locationNumber].locationpoint);
    }
    return { init : init, resize : resize, toggleLocation : toggleLocation}
})();

$(document).ready(function(){
    Brooklyn.location.init();
});




