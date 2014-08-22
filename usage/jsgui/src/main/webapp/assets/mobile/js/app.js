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
var app = angular.module('BrooklynMobile', [ 
  "ngRoute",
  "ngTouch",
  "mobile-angular-ui", 
  "BrooklynApp.Services",
  "BrooklynApp.filters",
  "BrooklynApp.controllers",
  "pascalprecht.translate",
  "ngCookies"]);

app.config(function($routeProvider, $translateProvider) {
	//Defaults root to applications.  Should change in the future
	$routeProvider.when('/', {
		templateUrl : "/assets/mobile/js/templates/applicationsList.html",
		controller:"ApplicationListController"
	});
	
	//Lists applications
	$routeProvider.when('/v1/applications', {
		templateUrl : "/assets/mobile/js/templates/applicationsList.html",
		controller:"ApplicationListController"
	});
	
	//List entities
	$routeProvider.when('/v1/applications/:appId/entities/:id', {
		templateUrl : "/assets/mobile/js/templates/entitiesList.html",
		controller:"EntityListController"
	});
	$routeProvider.when('/v1/applications/:appId/entities', {
		templateUrl : "/assets/mobile/js/templates/entitiesList.html",
		controller:"EntityListController"
	});
	$routeProvider.when('/v1/applications/:appId', {
		templateUrl : "/assets/mobile/js/templates/entitiesList.html",
		controller:"EntityListController"
	});
	
	//Application and entity details
	$routeProvider.when('/v1/applications/:appId/entities/:id/summary', {
		templateUrl : "/assets/mobile/js/templates/entitySummary.html",
		controller:"EntityDetailsController"
	});
	$routeProvider.when('/v1/applications/:appId/summary', {
		templateUrl : "/assets/mobile/js/templates/entitySummary.html",
		controller:"EntityDetailsController"
	});
	
	$translateProvider.useStaticFilesLoader({
		  prefix: '/assets/mobile/js/i18n/',
		  suffix: '.json'
		});
	//$translateProvider.useLocalStorage();
	$translateProvider.preferredLanguage('en-us');
	
});

app.controller('MainController', function($rootScope, $scope) {

	$rootScope.$on("$routeChangeStart", function() {
		$rootScope.loading = true;
	});

	$rootScope.$on("$routeChangeSuccess", function() {
		$rootScope.loading = false;
	});
});