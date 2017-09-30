var moment = require('moment');
var angular = require('angular');

var myApp = angular.module('myApp', [require('ng-admin')]);
myApp.config(['NgAdminConfigurationProvider', function (NgAdminConfigurationProvider) {
    var nga = NgAdminConfigurationProvider;

    var admin = nga.application('Scraper').debug(true);

    var scrapArchiveJob = nga.entity('scrapArchiveJob').url(function (entityName, viewType, identifierValue) {
        return '/scrap/archive/job' + (viewType === 'DeleteView' || viewType === 'ShowView' ? '/' + identifierValue : '');
    }).label('Scrap archive jobs');


    var scrapArchJobActionsTemp = '<ma-show-button entry="entry" entity="entity" size="xs"></ma-show-button>' +
        '<ma-delete-button entry="entry" entity="entity" size="xs"></ma-delete-button>' +
        '<send-command entry="entry" job-type="archive" entity="entity" size="xs" command="stop"></send-command>' +
        '<send-command entry="entry" job-type="archive" entity="entity" size="xs" command="start"></send-command>';

    scrapArchiveJob.listView().fields([
        nga.field('name').label('Job name'),
        nga.field('urls', 'text').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                var res = value.join();
                if (res.length > 125) {
                    res = res.substring(0, 120) + '...';
                }
                return res;
            }
        }).label('URLs'),
        nga.field('state'),
        nga.field('progress').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return Math.round(value);
            }
        })
    ]).listActions(scrapArchJobActionsTemp).actions('<auto-reload-page></auto-reload-page><ma-create-button entity="entity"></ma-create-button>');

    scrapArchiveJob.creationView().fields([
        nga.field('name').label('Job name').attributes({placeholder: 'Please type name of the job'})
            .validation({required: true, maxlength: 250}),
        nga.field('urls', 'text').attributes({placeholder: 'Please type URLs separated by comma.'})
            .transform(function allCaps(value, entry) {
                return value.split(",").map(function (url) {
                    return url.trim();
                });
            }).label('URLs').validation({required: true}),
        nga.field('fromYear', 'number').defaultValue(scrapArchConf.fromYear).label('From Year'),
        nga.field('toYear', 'number').defaultValue(scrapArchConf.toYear).label('To Year'),
        nga.field('elastic.indexName').defaultValue(scrapArchConf.elastic.indexName).validation({required: true})
            .label('Elasticsearch index name'),
        nga.field('fetchThreadsNum', 'number').validation({required: true})
            .defaultValue(scrapArchConf.fetchThreadsNum).label('Fetch threads number'),
        nga.field('crawlIndexesHost').validation({required: true})
            .defaultValue(scrapArchConf.crawlIndexesHost).label('Archive index URL'),
        nga.field('warcFilesHost').validation({required: true})
            .defaultValue(scrapArchConf.warcFilesHost).label('Archive URL'),
        nga.field('crawlLinksLimit', 'number').label('Scrap links limit')
            .attributes({placeholder: 'Please specify links limit. If you want to scrap all links, leave it empty.'})
    ]).onSubmitError(['error', 'form', 'progression', 'notification', function (error, form, progression, notification) {
        error.data.errors.forEach(function (error) {
            if (form[error.field]) {
                form[error.field].$valid = false;
            }
        });

        progression.done();
        notification.log(error.data.message, {addnCls: 'humane-flatty-error'});
        return false;
    }]);


    scrapArchiveJob.showView().fields([
        nga.field('name').label('Job name'),
        nga.field('urls').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return value.join();
            }
        }).label('URLs'),
        nga.field('state'),
        nga.field('startTime').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return moment(value).format('HH:mm:ss DD/MM/YY');
            }
        }),
        nga.field('finishTime').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return moment(value).format('HH:mm:ss DD/MM/YY');
            }
        }),
        nga.field('fromYear').label('From Year'),
        nga.field('toYear').label('To Year'),
        nga.field('elastic.indexName').label('Elasticsearch index name'),
        nga.field('fetchThreadsNum', 'number').label('Fetch threads number'),
        nga.field('crawlIndexesHost').label('Archive index URL'),
        nga.field('warcFilesHost').label('Archive URL'),
        nga.field('crawlLinksLimit', 'number').label('Scrap links limit')
    ]).title('Scrap archive job {{entry.values.name}} detail');

    scrapArchiveJob.deletionView().title('Delete scrap archive job {{entry.values.name}}');

    admin.addEntity(scrapArchiveJob);

    var scrapJob = nga.entity('scrapJob').url(function (entityName, viewType, identifierValue) {
        return '/scrap/job' + (viewType === 'DeleteView' || viewType === 'ShowView' ? '/' + identifierValue : '');
    }).label('Scrap website jobs');


    var scrapJobActionsTemp = '<ma-show-button entry="entry" entity="entity" size="xs"></ma-show-button>' +
        '<ma-delete-button entry="entry" entity="entity" size="xs"></ma-delete-button>' +
        '<send-command entry="entry" job-type="current" entity="entity" size="xs" command="stop"></send-command>' +
        '<send-command entry="entry" job-type="current" entity="entity" size="xs" command="start"></send-command>';
    scrapJob.listView().fields([
        nga.field('name').label('Job name'),
        nga.field('urls', 'text').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return value.join();
            }
        }).label('URLs'),
        nga.field('interval').map(function (value, entry) {
            return moment.duration(value, 'seconds').humanize();
        }),
        nga.field('state'),
        nga.field('progress').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return Math.round(value);
            }
        })
    ]).listActions(scrapJobActionsTemp).actions(['<auto-reload-page></auto-reload-page><ma-create-button entity="entity"></ma-create-button>']);

    scrapJob.creationView().fields([
        nga.field('name').label('Job name').attributes({placeholder: 'Please type name of the job'})
            .validation({required: true, maxlength: 250}),
        nga.field('urls', 'text').transform(function allCaps(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return value.split(",").map(function (url) {
                    return url.trim();
                });
            }
        }).label('URLs').attributes({placeholder: 'Please type URLs separated by comma.'}).validation({required: true}),
        nga.field('depth', 'number').defaultValue(scrapConf.depth).validation({required: true}),
        nga.field('interval').transform(function allCaps(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                value = value.trim();
                var periodLetter = value.slice(-1);
                var number = parseInt(value.substring(0, value.length - 1).trim());
                return Math.round(moment.duration(number, periodLetter).asSeconds());
            }
        }).defaultValue(moment.duration(scrapConf.interval, 'seconds').as('hours') + 'h').validation({required: true}),
        nga.field('elasticIndexName').defaultValue(scrapConf.elasticIndexName).label('Elasticsearch index name').validation({required: true}),
        nga.field('extractArticle', 'boolean').choices([
            {value: true, label: 'enabled'},
            {value: false, label: 'disabled'}
        ]).defaultValue(scrapConf.extractArticle).label('Extract article').validation({required: true})
    ]).onSubmitError(['error', 'form', 'progression', 'notification', function (error, form, progression, notification) {
        error.data.errors.forEach(function (error) {
            if (form[error.field]) {
                form[error.field].$valid = false;
            }
        });

        progression.done();
        notification.log(error.data.message, {addnCls: 'humane-flatty-error'});
        return false;
    }]);

    scrapJob.showView().fields([
        nga.field('name').label('Job name'),
        nga.field('urls').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return value.join();
            }
        }).label('URLs'),
        nga.field('state'),
        nga.field('depth', 'number'),
        nga.field('interval').map(function (value, entry) {
            return moment.duration(value, 'seconds').humanize();
        }),
        nga.field('startTime').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return moment(value).format('HH:mm:ss DD/MM/YY');
            }
        }),
        nga.field('finishTime').map(function truncate(value, entry) {
            if (value === null || value === undefined) {
                return value;
            } else {
                return moment(value).format('HH:mm:ss DD/MM/YY');
            }
        }),
        nga.field('elasticIndexName').label('Elasticsearch index name'),
        nga.field('extractArticle', 'boolean').label('Extract article')
    ]).title('Scrap website job {{entry.values.name}} detail');

    scrapJob.deletionView().title('Delete scrap website job {{entry.values.name}}');

    admin.addEntity(scrapJob);

    nga.configure(admin);
}]);

myApp.directive('sendCommand', ['$location', function ($location) {
    return {
        restrict: 'E',
        scope: {
            'entry': '<',
            'entity': '<',
            'command': '@',
            'jobType': '@'
        },
        template: '<a ng-if="showButton()" class="btn btn-default btn-xs" ng-click="sendCommand()">{{command | capitalize}}</a>',
        controller: ['$http', '$scope', '$state', 'notification',
            function ($http, $scope, $state, notification) {
                $scope.sendCommand = function () {
                    var url = '/scrap/' + ($scope.jobType === 'archive' ? 'archive/' : '')
                        + 'job/' + $scope.entry.values.id + '/command';
                    $http.post(url, {command: $scope.command}, {
                        headers: {Accept: 'application/json'},
                        responseType: 'json'
                    }).then(function (response) {
                        $state.reload();
                    }, function errorCallback(response) {
                        notification.log(getErrMsg(response), {addnCls: 'humane-flatty-error'});
                    });
                };

                $scope.showButton = function () {
                    var state = $scope.entry.values.state;
                    if ($scope.command === 'start') {
                        return state !== 'running' && state !== 'stopping';
                    } else if ($scope.command === 'stop') {
                        return state === 'running' && state !== 'stopping';
                    }
                }
            }
        ]
    };
}]);

myApp.directive('autoReloadPage', ['$location', function ($location) {
    return {
        restrict: 'E',
        scope: {},
        controller: ['$scope', '$interval', '$state', 'progression',
            function ($scope, $interval, $state, progression) {
                var fetchPageInterval = $interval(function () {
                    $state.reload();
                    progression.done();
                }, 10000);

                $scope.$on('$destroy', function () {
                    if (angular.isDefined(fetchPageInterval)) {
                        $interval.cancel(fetchPageInterval);
                        fetchPageInterval = undefined;
                    }
                });
            }
        ]
    };
}]);

myApp.filter('capitalize', function () {
    return function (input) {
        return (!!input) ? input.charAt(0).toUpperCase() + input.slice(1) : '';
    }
});

function getErrMsg(response) {
    var data = response.data;
    return data && data.message ? data.message : 'Status: ' + response.status + " " + response.statusText;
}
