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
var bklnControllers = angular.module('BrooklynApp.controllers',['ngResource']);

//Brooklyn ApplicationList controller
bklnControllers.controller('ApplicationListController', function($scope, $interval, ApplicationService){
	//
	$scope.reloadTimer;
	$scope.loadData = function(){
		ApplicationService.query({}, function (applications){
			$scope.applications = applications;
			console.log('ApplicationListController: new data loaded');
		});
	};
	
	$scope.showDetails = function($event){
		alert("swipe happened");
		$event.stopPropagation();
		console.dir ($event);
	};
	
	$scope.$on('$viewContentLoaded', function() {
	    console.log('ApplicationListController: loadeding');
	    $scope.loadData();
	    console.log('ApplicationListController: requested data');
	    $scope.reloadTimer = $interval($scope.loadData, 48000);
	    console.log('ApplicationListController: reload timer set');
	    console.log('ApplicationListController: loaded');
	  });
	
	$scope.$on('$destroy', function() {
	    console.log('ApplicationListController: unloading');
	    if (angular.isDefined($scope.reloadTimer)) {
	        $interval.cancel($scope.reloadTimer);
	        $scope.reloadTimer = undefined;
	      }
	    console.log('ApplicationListController: reload timer stopped');
	    console.log('ApplicationListController: unloaded');
	  });
	
});


bklnControllers.controller('EntityListController', function($scope, $http, $interval, $location, $routeParams){
	
	$scope.reloadTimer;
	$scope.path = $location.path().replace("#",'');
	var shownPath = false;
	$scope.loadData = function(){
		
		$http({method: 'GET', url: $scope.path}).
	    success(function(data) {
	    	$scope.entity = data;
	    	$scope.name = (data.spec) ? data.spec.name : data.name;
	    	var children = (data.links.children) ? data.links.children : data.links.entities ;
	    	$scope.back = (data.links.parent) ? data.links.parent : "/v1/applications";
	    	if (!shownPath) {
	    		console.info ("Current path: "+ $location.path());
	    		console.info ("Back path: "+ $scope.back);
	    		shownPath = true;
			}
	    	console.log('EntityListController: new data loaded');
	    	$http({method: 'GET', url: children}).
	        success(function(data) {
	        	$scope.entities = data;
	        	//get status sequentially..
	        	angular.forEach($scope.entities, function (value, index){
	        		$http.get(value.links.sensors + "/service.state")
	        		.success(function(data){
	        			data = data.replace(/"/g,'');
	        			value.status = (data.length > 0) ? data : "Unknown";
	        		});
	        	});
	        	
	        });
	    });
	};
		
	$scope.$on('$viewContentLoaded', function() {
	    console.log('EntityListController: loadeding');
	    $scope.loadData();
	    console.log('EntityListController: requested data');
	    $scope.reloadTimer = $interval($scope.loadData, 48000);
	    console.log('EntityListController: reload timer set');
	    console.log('EntityListController: loaded');
	  });
	
	$scope.$on('$destroy', function() {
	    console.log('EntityListController: unloading');
	    if (angular.isDefined($scope.reloadTimer)) {
	        $interval.cancel($scope.reloadTimer);
	        $scope.reloadTimer = undefined;
	      }
	    console.log('EntityListController: reload timer stopped');
	    console.log('EntityListController: unloaded');
	  });
	
});

bklnControllers.controller('EntityDetailsController', function($scope, $filter, $window, $location, $http, $resource, $routeParams){

	
	$scope.goBack = function (){
		$window.history.back();
	};
	
	$scope.init = function(){
		$scope.path = $location.path().replace("#",'');
		
		$scope.selfLink = $location.path().replace("/summary",'');
		
		$http({method: 'GET', url: $scope.selfLink}).
	    success(function(data) {
	    	$scope.entity = data;
	    	$scope.name = (data.spec) ? data.spec.name : data.name;
	    	var sensors = ($scope.entity.links.sensors) ? $scope.entity.links.sensors : $scope.selfLink + "/sensors"; 
	    	$http({method:'GET', url:$scope.entity.links.effectors}).
	    	success(function(data){
	    		$scope.entity.effectors = data;
	    		console.dir (data);
	    	});
	    	
	    	$http({method: 'GET', url: sensors + '/current-state'}).
		    success(function(data) {
		    	$scope.entity.status = (data["service.state"]) ? data["service.state"].replace(/"/g,'') : null;
		    	$scope.entity.isUp = data["service.isUp"];
		    });

	    	var activities = ($scope.entity.links.activities) ? $scope.entity.links.activities : $scope.selfLink + "/activities"; 
	    	$http({method: 'GET', url: activities}).
		    success(function(data) {
		    	console.info(activities);
		    	console.dir(data);
		    	$scope.entity.activities = data;
		    });
	    	var config = ($scope.entity.links.config) ? $scope.entity.links.config : $scope.selfLink + "/config"; 
	    	var configUrl = config +  '/current-state';
	    	$http({method: 'GET', url: configUrl}).
		    success(function(data) {
		    	console.info(configUrl);
		    	console.dir(data);
		    	var config = [];
		    	angular.forEach(data, function (v, k){
		    		config.push({"key": k, "value":v});
		    	});
		    	$scope.entity.config = config;
		    });
	    	
			console.dir ($scope.entity);
	    });
	};
	
	$scope.isTopLevelActivity = function(input){
		var submitter = input.submittedByTask;
		return (submitter == null ||(submitter != null && $filter('filter')($scope.entity.activities,{"id":submitter.metadata.id}).length == 0 ));
	};
	
	$scope.expunge = function (){
		//TODO: show dialog requesting if user wants to release resources
		var expungeURL = $scope.selfLink + "/expunge?replace=false";
		$http({method: 'POST', url: expungeURL}).
	    success(function(data) {
	    	console.info(expungeURL);
	    	console.dir(data);
	    });
		
		
	};
	
	$scope.executeEffector = function (uri){
		var effector = uri + "?timeout=0";
		var data = ""; 
		console.info ("Executing: " + effector);
		$http({method: 'POST', url: effector, data:data, headers: {'Content-Type': 'application/x-www-form-urlencoded'}}).
	    success(function(data) {
	    	console.info("Success");
	    	console.dir(data);
	    }).
	    error(function(data, status){
	    	console.error ("Failed: " + status);
	    	console.dir (data);
	    });
	};
	$scope.init();
	
});