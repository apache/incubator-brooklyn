/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
//shim to access google maps with require.js -- courtesy https://github.com/p15martin/google-maps-hello-world/
define(
        [ "async!https://maps.googleapis.com/maps/api/js?sensor=false" ],
        function() {
            var locationMarkers = {};
            // meters squared per entity
            var area_per_entity = 300000000000;
            var local = {
                addMapToCanvas: function( mapCanvas, lat, longitude, zoom ) {
                    var myOptions = {
                            center: new google.maps.LatLng( lat, longitude ),
                            zoom: zoom,
                            mapTypeId: google.maps.MapTypeId.SATELLITE
                    };

                    return new google.maps.Map(mapCanvas, myOptions);
                },

                // TODO info window; massive code tidy
                drawCircles: function(map, data) {
                    var newLocs = {};
                    var lm;
                    _.each(data, function(it) {
                        var id = it.id;
                        if (it.latitude == null || it.longitude == null || (it.latitude == 0 && it.longitude == 0)) {
                            // Suppress circle if not set or at (0,0); slightly clumsy, but workable
                        } else if (lm = locationMarkers[id]) {
                            // Update
                            var latlng = new google.maps.LatLng(it.latitude, it.longitude);

                            lm.circle.setRadius(local.radius(local.computeLocationArea(it.leafEntityCount)));
                            lm.circle.setCenter(latlng);
                            lm.marker.setPosition(latlng);
                            lm.marker.setTitle(it.name);
//                            lm.infoWindow.setPairs(l);

                            newLocs[id] = lm;
                        } else {
                            // Add
                            var circle = local.drawCircle(map, it.latitude, it.longitude, local.radius(local.computeLocationArea(it.leafEntityCount)));

                            var marker = new google.maps.Marker({
                                map: map,
                                position: new google.maps.LatLng(it.latitude, it.longitude),
                                title: it.name
                            });

                            // TODO from old grails app
//                            var infoWindow = new Brooklyn.gmaps.ListInfoWindow(l, map, marker);

                            circle.bindTo('center', marker, 'position');
                            newLocs[id] = {circle: circle,
                                    marker: marker
//                                    ,
//                                    infoWindow: infoWindow
                                    };
                        }
                    })

                    // TODO yuck, we assume location markers (static field) are tied to map (supplied)
                    for (var marker in locationMarkers) {
                        if (! newLocs[marker]) {
                            // location has been removed
                            lm = locationMarkers[marker];
                            lm.circle.setMap(null);
                            lm.marker.setMap(null);
                            lm.infoWindow.getInfoWindow().setMap(null);
                        }
                    }
                    locationMarkers = newLocs;
                },
                resetCircles: function() {
                    locationMarkers = {};
                },

                drawCircle: function(map, lat, lng, radius) {
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
                },

                /* Returns the area in square meters that a circle should be to represent
                 * count entities at a location. */
                computeLocationArea: function(count) {
                    return area_per_entity * count;
                },

                /* Returns the radius of a circle of the given area. */
                radius: function(area) {
                    return Math.sqrt(area / Math.PI);
                }

//                function drawCirclesFromJSON(json) {
//                    var newLocs = {};
//                    var id;
//                    var lm;
//
//                    for (id in json) {
//                        var l = json[id];
//                        if (l.lat == null || l.lng == null || (l.lat == 0 && l.lng == 0)) {
//                            // Suppress circle if not set or at (0,0); slightly clumsy, but workable
//                        } else if (lm = locationMarkers[id]) {
//                            // Update
//                            var latlng = new google.maps.LatLng(l.lat, l.lng);
//
//                            lm.circle.setRadius(radius(location_area(l.entity_count)));
//                            lm.circle.setCenter(latlng);
//                            lm.marker.setPosition(latlng);
//                            lm.infoWindow.setPairs(l);
//
//                            newLocs[id] = lm;
//                        } else {
//                            // Add
//                            var circle = drawCircle(l.lat, l.lng, radius(location_area(l.entity_count)));
//
//                            var marker = new google.maps.Marker({
//                                map: map,
//                                position: new google.maps.LatLng(l.lat, l.lng)
//                            });
//
//                            var infoWindow = new Brooklyn.gmaps.ListInfoWindow(l, map, marker);
//
//                            circle.bindTo('center', marker, 'position');
//
//                            newLocs[id] = {circle: circle,
//                                           marker: marker,
//                                           infoWindow: infoWindow};
//                        }
//                    }
//
//                    for (id in locationMarkers) {
//                        if (! newLocs[id]) {
//                            // location has been removed
//                            lm = locationMarkers[id];
//                            lm.circle.setMap(null);
//                            lm.marker.setMap(null);
//                            lm.infoWindow.getInfoWindow().setMap(null);
//                        }
//                    }
//                    locationMarkers = newLocs;
//                }

            }
            
            return local;
        }
        
);
