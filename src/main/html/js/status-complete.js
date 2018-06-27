var app = angular.module('status', []);

app.directive('tbStatus', function() {
    return {
        restrict: 'E',
template: '<style>\n'
+ '.statusProblem {\n'
+ 'background-color: rgba(187, 80, 80, 0.75);\n'
+ '}\n'
+ '.statusSuccess {\n'
+ 'background-color: rgba(80, 187, 80, 0.75)\n'
+ '}\n'
+ '.statusInfo {\n'
+ 'background-color: rgba(80, 80, 187, 0.75)\n'
+ '}\n'
+ '.statusTextStart {\n'
+ 'font-size: 0em;\n'
+ 'opacity: 0.25;\n'
+ '}\n'
+ '.statusTextEnd {\n'
+ 'font-size: 1.5em;\n'
+ 'opacity: 0.75;\n'
+ '}\n'
+ '.statusStart {\n'
+ 'min-width: 0px;\n'
+ 'min-height: 0px;\n'
+ 'height: 0px;\n'
+ '} \n'
+ '.statusEnd {\n'
+ 'height: 45%;\n'
+ 'min-height: 45%;\n'
+ 'min-width: 65.5%;\n'
+ '}\n'
+ '.statusEndSmall {\n'
+ 'height: 25%;\n'
+ 'top: 70% !important;\n'
+ '/*right: 0% !important;*/\n'
+ 'min-height: 25%;\n'
+ 'min-width: 35%;\n'
+ '}\n'
+ '.statusEndSmallActions {\n'
+ 'height: 50%;\n'
+ 'min-height: 25%;\n'
+ 'min-width: 40%;\n'
+ '}\n'
+ '.statusBoxStart {\n'
+ 'top: 50%;\n'
+ 'transform:rotate(360deg);\n'
+ '-moz-transform:rotate(360deg);\n'
+ '-webkit-transform:rotate(360deg);    \n'
+ 'opacity: 0.25;\n'
+ '}\n'
+ '.statusBoxEnd {\n'
+ 'left: 25%;\n'
+ 'top: 50%;\n'
+ 'transform:rotate(-7.5deg);\n'
+ '-moz-transform:rotate(-7.5deg);\n'
+ '-webkit-transform:rotate(-7.5deg);\n'
+ 'opacity: 0.85;\n'
+ '}\n'
+ '.statusShowing {\n'
+ 'left: 0% !important;\n'
+ '}\n'
+ '.statusHiding {\n'
+ 'left: 100%;\n'
+ '}\n'
+ '.status {\n'
+ 'position: fixed;\n'
+ 'border: white 2px dashed;\n'
+ 'border-radius: 9em;\n'
+ 'display: block;\n'
+ 'font-family: \'Varela Round\';\n'
+ 'font-size: 2em;\n'
+ 'color: white;\n'
+ 'transition-property: all;\n'
+ 'transition-duration: 1.5s;\n'
+ 'transition-timing-function: cubic-bezier;\n'
+ '}\n'
+ '@media (max-width: 640px), handheld {\n'
+ '.status {\n'
+ 'font-size: 1em;\n'
+ '}\n'
+ '}\n'
+ '.actionButton {\n'
+ 'font-size: 2rem;\n'
+ 'opacity: 0.9;\n'
+ '}\n'
+ '.status table {\n'
+ 'width: 100%;\n'
+ 'height: 100%;\n'
+ 'margin-left: 5px;\n'
+ 'margin-right: 5px;\n'
+ 'transition: all 1.5s ease;\n'
+ '}\n'
+ '.status td {\n'
+ 'width: 100%;\n'
+ 'text-align: center;\n'
+ 'transition-property: all;\n'
+ 'transition-duration: 1.5s;\n'
+ 'transition-timing-function: cubic-bezier;\n'
+ '}\n'
+ '.firstMsg {\n'
+ 'font-size: 0.8em;\n'
+ 'font-color: #cccccc;\n'
+ '}\n'
+ '</style>\n'
+ '<div class=\'status\' ng-click=\'clear()\' ng-show=\'statusShowing\' \n'
+ 'ng-class="{statusSuccess : !problem, statusInfo : info && !problem, statusBoxStart: !statusSize, statusBoxEnd : statusSize, statusStart : !statusSize, statusProblem : problem, statusEnd : statusSize && !small, statusEndSmall : statusSize && small && !actions, statusEndSmallActions : statusSize && small && actions, statusHiding : hiding, statusShowing : !hiding}">\n'
+ '<table>\n'
+ '<tr>\n'
+ '<td ng-class="{statusTextStart: !statusSize, statusTextEnd : statusSize, statusStart : !statusSize, statusEnd : statusSize}">\n'
+ '{{statusValue}}\n'
+ '<span ng-repeat="action in actions" class="statusAction">\n'
+ '<br/>\n'
+ '<button ng-class="{statusTextStart : !statusSize, statusTextEnd : statusSize}" \n'
+ 'class=\'btn btn-primary actionButton\' \n'
+ 'ng-click="action.action()">{{action.name}}</button>\n'
+ '</span>\n'
+ '<span ng-show="!msgShown" class="firstMsg">\n'
+ '<br/>\n'
+ '(click to hide)\n'
+ '</span>\n'
+ '</td>\n'
+ '</tr>\n'
+ '</table>\n'
+ '</div>\n',

        controller: Status
    };
});

function Status($scope, $status) {
    $scope.msgShown = false;
    $scope.hiding = true;
    $scope.timeout = null;
    $scope.problem = false;
    $scope.clear = $status.clear;
    var onTimeout = showStatus;

    function startTimer(to, func) {
        change();
        if (typeof to === 'function') {
            func = to;
            to = 1500;
        }
        to = to || 1500;
        onTimeout = func;
        $scope.timeout = setTimeout(function() {
            $scope.$apply(onTimeout);
        }, to);
    }

    function showStatus() {
        $scope.statusShowing = true;
        $scope.statusSize = true;
        startTimer($scope.clearAfter || 25000, hideStatus);
    }

    function hideStatus() {
        if (!$scope.$$phase) {
            
            $scope.$apply(function() {
                startTimer(statusHidden);
                $scope.statusSize = false;
                $scope.hiding = true;
            });
        } else {
            startTimer(statusHidden);
            $scope.statusSize = false;
            $scope.hiding = true;
        }
    }

    function statusHidden() {
        $scope.statusShowing = false;
        $scope.statusSize = false;
        $scope.statusValue = null;
        $scope.msgShown = true;
        $scope.actions = null;
        $scope.problem = false;
        $scope.info = false;
        $scope.small = false;
    }

    function change() {
        if ($scope.timeout) {
            clearTimeout($scope.timeout);
        }
    }

    function statusSet(val) {
        $scope.small = val.msg.length <= 45;
        $scope.statusShowing = true;
        $scope.statusValue = val.msg;
        $scope.hiding = false;
        startTimer(1, showStatus);
    }

    function statusCleared() {
        change();
        $scope.hiding = true;
        hideStatus();
    }

    $status.addListener(function(val) {
        if (val) {
            if (val.msg === $scope.statusValue) {
                return;
            }
            change();
            $scope.actions = val.actions;
            $scope.info = val.info;
            $scope.problem = typeof val.problem === 'undefined' ? false : val.problem;
            if (val.action) {
                $scope.actions = [
                    {
                        action: val.action,
                        name: val.actionName
                    }
                ];
            }
            $scope.clearAfter = val.clearAfter;
            statusSet(val);
        } else {
            statusCleared();
        }
    });
}

app.service('$status', function($rootScope) {
    var self = this;

    var listeners = [];
    function addListener(listener) {
        listeners.push(listener);
    }
    this.addListener = addListener;
    
    function removeListener(listener) {
        for (var i=listener.length-1; i >=0; i--) {
            if (listeners[i] === listener) {
                listeners.splice(i, 1);
            }
        }
    }
    this.removeListener = removeListener;

    function clear() {
        $rootScope.status = null;
        for (var i = 0; i < listeners.length; i++) {
            listeners[i](null);
        }
    }

    this.clear = clear;
    $rootScope.clearStatus = clear;

    this.setStatus = function(val) {
        $rootScope.status = val;
        for (var i = 0; i < listeners.length; i++) {
            listeners[i](val);
        }
    };

    this.setSuccess = function(val) {
        self.setStatus($rootScope.status = {
            msg: val,
            problem: false
        });
    };

    this.setInfo = function(val) {
        self.setStatus($rootScope.status = {
            msg: val,
            info: true
        });
    };

    this.setProblem = function(val) {
        self.setStatus($rootScope.status = {
            msg: val,
            problem: true
        });
    };

    this.getStatus = function() {
        return $rootScope.status;
    };

    this.onError = function(err, code) {
        if (typeof err === 'object' && err.message) {
            self.setProblem(err.message);
        } else if (typeof err === 'object') {
            self.setProblem(JSON.stringify(err));
        } else {
            if (/^<html/.test(err)) {
                err = err.replace(/<.*?>/g, '');
            }
            self.setProblem(err);
        }
    };

    this.createErrorHandler = function(cb) {
        return function(err, code) {
            self.onError(err, code);
            if (cb) {
                cb(err, code);
            }
        };
    };
});
