(function ($) {
    /*
     ** Zabbix
     ** Copyright (C) 2001-2017 Zabbix SIA
     **
     ** This program is free software; you can redistribute it and/or modify
     ** it under the terms of the GNU General Public License as published by
     ** the Free Software Foundation; either version 2 of the License, or
     ** (at your option) any later version.
     **
     ** This program is distributed in the hope that it will be useful,
     ** but WITHOUT ANY WARRANTY; without even the implied warranty of
     ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
     ** GNU General Public License for more details.
     **
     ** You should have received a copy of the GNU General Public License
     ** along with this program; if not, write to the Free Software
     ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
     **/
    window.flickerfreeScreen = {
        screens: [],

        add: function (screen, server) {
            // init screen item
            this.screens[screen.id] = screen;
            this.screens[screen.id].interval = (screen.interval > 0) ? screen.interval * 1000 : 0;
            this.screens[screen.id].timestamp = 0;
            this.screens[screen.id].timestampResponsiveness = 0;
            this.screens[screen.id].timestampActual = 0;
            this.screens[screen.id].isRefreshing = false;
            this.screens[screen.id].isReRefreshRequire = false;
            this.screens[screen.id].error = 0;

            // SCREEN_RESOURCE_MAP
            if (screen.resourcetype === 2) {
                this.screens[screen.id].data = new SVGMap(this.screens[screen.id].data, server);
            }

            // init refresh plan
            if (screen.isFlickerfree && screen.interval > 0) {
                this.screens[screen.id].timeoutHandler = window.setTimeout(
                        function () {
                            window.flickerfreeScreen.refresh(screen.id);
                        },
                        this.screens[screen.id].interval
                        );
            }
        },

        refresh: function (id, isSelfRefresh) {},

        refreshAll: function (period, stime, isNow) {
            for (var id in this.screens) {
                var screen = this.screens[id];

                if (!empty(screen.id) && typeof screen.timeline !== 'undefined') {
                    screen.timeline.period = period;
                    screen.timeline.stime = stime;
                    screen.timeline.isNow = isNow;

                    // restart refresh execution starting from Now
                    clearTimeout(screen.timeoutHandler);
                    this.refresh(id, true);
                }
            }
        },

        refreshHtml: function (id, ajaxUrl) {
            var screen = this.screens[id];

            if (screen.isRefreshing) {
                this.calculateReRefresh(id);
            } else {
                screen.isRefreshing = true;
                screen.timestampResponsiveness = new CDate().getTime();

                window.flickerfreeScreenShadow.start(id);

                var ajaxRequest = $.ajax({
                    url: ajaxUrl.getUrl(),
                    type: 'post',
                    data: {},
                    dataType: 'html',
                    success: function (html) {
                        // Get timestamp and error message from HTML.
                        var htmlTimestamp = null,
                                msg_bad = null;

                        $(html).each(function () {
                            var obj = $(this);

                            if (obj.hasClass('msg-bad')) {
                                msg_bad = obj;
                            } else if (obj.prop('nodeName') === 'DIV') {
                                htmlTimestamp = obj.data('timestamp');
                            }
                        });

                        $('.msg-bad').remove();

                        // set message
                        if (msg_bad) {
                            $(msg_bad).insertBefore('.article > :first-child');
                            html = $(html).not('.msg-bad');
                        }

                        // set html
                        if ($('#flickerfreescreen_' + id).data('timestamp') < htmlTimestamp) {
                            $('#flickerfreescreen_' + id).replaceWith(html);

                            screen.isRefreshing = false;
                            screen.timestamp = htmlTimestamp;

                            window.flickerfreeScreenShadow.isShadowed(id, false);
                            window.flickerfreeScreenShadow.fadeSpeed(id, 0);
                            window.flickerfreeScreenShadow.validate(id);
                        }
                        chkbxRange.init();
                    },
                    error: function () {
                        window.flickerfreeScreen.calculateReRefresh(id);
                    }
                });

                $.when(ajaxRequest).always(function () {
                    if (screen.isReRefreshRequire) {
                        screen.isReRefreshRequire = false;
                        window.flickerfreeScreen.refresh(id, true);
                    }
                });
            }
        },

        refreshMap: function (id) {
            var screen = this.screens[id];

            if (screen.isRefreshing) {
                this.calculateReRefresh(id);
            } else {
                screen.isRefreshing = true;
                screen.error = 0;
                screen.timestampResponsiveness = new CDate().getTime();

                window.flickerfreeScreenShadow.start(id);

                var url = new Curl(screen.data.options.refresh);
                url.setArgument('curtime', new CDate().getTime());

                jQuery.ajax({
                    'url': url.getUrl()
                })
                        .error(function () {
                            screen.error++;
                            window.flickerfreeScreen.calculateReRefresh(id);
                        })
                        .done(function (data) {
                            screen.isRefreshing = false;
                            screen.data.update(data);
                            screen.timestamp = screen.timestampActual;
                            window.flickerfreeScreenShadow.end(id);
                        });
            }
        },

        refreshImg: function (id, successAction) {
            var screen = this.screens[id];

            if (screen.isRefreshing) {
                this.calculateReRefresh(id);
            } else {
                screen.isRefreshing = true;
                screen.error = 0;
                screen.timestampResponsiveness = new CDate().getTime();

                window.flickerfreeScreenShadow.start(id);

                $('#flickerfreescreen_' + id + ' img').each(function () {
                    var domImg = $(this),
                            url = new Curl(domImg.attr('src')),
                            on_dashboard = function () {};// timeControl.objectList[id].onDashboard;

                    url.setArgument('screenid', empty(screen.screenid) ? null : screen.screenid);
                    url.setArgument('updateProfile', (typeof screen.updateProfile === 'undefined')
                            ? null : +screen.updateProfile);
                    url.setArgument('period', empty(screen.timeline.period) ? null : screen.timeline.period);
                    url.setArgument('stime', window.flickerfreeScreen.getCalculatedSTime(screen));
                    url.setArgument('curtime', new CDate().getTime());

                    // Create temp image in buffer.
                    var img = $('<img>', {
                        'class': domImg.attr('class'),
                        'data-timestamp': new CDate().getTime(),
                        id: domImg.attr('id') + '_tmp',
                        name: domImg.attr('name'),
                        border: domImg.attr('border'),
                        usemap: domImg.attr('usemap'),
                        alt: domImg.attr('alt'),
                        css: {
                            position: 'relative',
                            zIndex: 2
                        }
                    })
                            .error(function () {
                                screen.error++;
                                window.flickerfreeScreen.calculateReRefresh(id);
                            })
                            .on('load', function () {
                                if (screen.error > 0) {
                                    return;
                                }

                                screen.isRefreshing = false;

                                // Re-refresh image.
                                var bufferImg = $(this);

                                if (bufferImg.data('timestamp') > screen.timestamp) {
                                    screen.timestamp = bufferImg.data('timestamp');

                                    // Set id.
                                    bufferImg.attr('id', bufferImg.attr('id').substring(0, bufferImg.attr('id').indexOf('_tmp')));

                                    // Set opacity state.
                                    if (window.flickerfreeScreenShadow.isShadowed(id)) {
                                        bufferImg.fadeTo(0, 0.6);
                                    }

                                    if (!empty(bufferImg.data('height'))) {
                                        //timeControl.changeSBoxHeight(id, bufferImg.data('height'));
                                    }

                                    // Set loaded image from buffer to dom.
                                    domImg.replaceWith(bufferImg);

                                    // Callback function on success.
                                    if (!empty(successAction)) {
                                        successAction();
                                    }

                                    // Rebuild timeControl sbox listeners.
                                    /*
                                     if (!empty(ZBX_SBOX[id])) {
                                     ZBX_SBOX[id].addListeners();
                                     }
                                     */

                                    window.flickerfreeScreenShadow.end(id);
                                }

                                if (screen.isReRefreshRequire) {
                                    screen.isReRefreshRequire = false;
                                    window.flickerfreeScreen.refresh(id, true);
                                }

                                if (on_dashboard) {
                                    //timeControl.updateDashboardFooter(id);
                                }
                            });

                    if (['chart.php', 'chart2.php', 'chart3.php'].indexOf(url.getPath()) > -1
                            && url.getArgument('outer') === '1') {
                        // Getting height of graph inside image. Only for line graphs on dashboard.
                        var heightUrl = new Curl(url.getUrl());
                        heightUrl.setArgument('onlyHeight', '1');

                        $.ajax({
                            url: heightUrl.getUrl(),
                            success: function (response, status, xhr) {
                                // 'src' should be added only here to trigger load event after new height is received.
                                img.data('height', +xhr.getResponseHeader('X-ZBX-SBOX-HEIGHT'));
                                img.attr('src', url.getUrl());
                            }
                        });
                    } else {
                        img.attr('src', url.getUrl());
                    }
                });
            }
        },

        refreshProfile: function (id, ajaxUrl) {
            var screen = this.screens[id];

            if (screen.isRefreshing) {
                this.calculateReRefresh(id);
            } else {
                screen.isRefreshing = true;
                screen.timestampResponsiveness = new CDate().getTime();

                var ajaxRequest = $.ajax({
                    url: ajaxUrl.getUrl(),
                    type: 'post',
                    data: {},
                    success: function (data) {
                        screen.timestamp = new CDate().getTime();
                        screen.isRefreshing = false;
                    },
                    error: function () {
                        window.flickerfreeScreen.calculateReRefresh(id);
                    }
                });

                $.when(ajaxRequest).always(function () {
                    if (screen.isReRefreshRequire) {
                        screen.isReRefreshRequire = false;
                        window.flickerfreeScreen.refresh(id, true);
                    }
                });
            }
        },

        calculateReRefresh: function (id) {
            var screen = this.screens[id],
                    time = new CDate().getTime();

            if (screen.timestamp + window.flickerfreeScreenShadow.responsiveness < time
                    && screen.timestampResponsiveness + window.flickerfreeScreenShadow.responsiveness < time) {
                // take of busy flags
                screen.isRefreshing = false;
                screen.isReRefreshRequire = false;

                // refresh anyway
                window.flickerfreeScreen.refresh(id, true);
            } else {
                screen.isReRefreshRequire = true;
            }
        },

        isRefreshAllowed: function (screen) {
            return false;
            //return empty(timeControl.timeline) ? true : timeControl.timeline.isNow();
        },

        getCalculatedSTime: function (screen) {
            /*            if (!empty(timeControl.timeline) && screen.timeline.period > timeControl.timeline.maxperiod) {
             return new CDate(timeControl.timeline.starttime() * 1000).getZBXDate();
             }
             */
            return (screen.timeline.isNow || screen.timeline.isNow === 1)
                    // 31536000 = 86400 * 365 = 1 year
                    ? new CDate((new CDate().setZBXDate(screen.timeline.stime) / 1000 + 31536000) * 1000).getZBXDate()
                    : screen.timeline.stime;
        },

        submitForm: function (formName) {
            var period = '',
                    stime = '';

            for (var id in this.screens) {
                if (!empty(this.screens[id])) {
                    period = this.screens[id].timeline.period;
                    stime = this.getCalculatedSTime(this.screens[id]);
                    break;
                }
            }

            $('form[name=' + formName + ']').append('<input type="hidden" name="period" value="' + period + '" />');
            $('form[name=' + formName + ']').append('<input type="hidden" name="stime" value="' + stime + '" />');
            $('form[name=' + formName + ']').submit();
        },

        cleanAll: function () {
            for (var id in this.screens) {
                var screen = this.screens[id];

                if (!empty(screen.id)) {
                    clearTimeout(screen.timeoutHandler);
                }
            }

            this.screens = [];
            ZBX_SBOX = {};
            /*
             for (var id in timeControl.objectList) {
             if (id !== 'scrollbar' && timeControl.objectList.hasOwnProperty(id)) {
             delete timeControl.objectList[id];
             }
             }
             */
            window.flickerfreeScreenShadow.cleanAll();
        }
    };

    window.flickerfreeScreenShadow = {

        timeout: 30000,
        responsiveness: 10000,
        timers: [],

        start: function (id) {
            if (empty(this.timers[id])) {
                this.timers[id] = {};
                this.timers[id].timeoutHandler = null;
                this.timers[id].ready = false;
                this.timers[id].isShadowed = false;
                this.timers[id].fadeSpeed = 2000;
                this.timers[id].inUpdate = false;
            }

            var timer = this.timers[id];

            if (!timer.inUpdate) {
                this.refresh(id);
            }
        },

        refresh: function (id) {
            var timer = this.timers[id];

            timer.inUpdate = true;

            clearTimeout(timer.timeoutHandler);
            timer.timeoutHandler = window.setTimeout(
                    function () {
                        window.flickerfreeScreenShadow.validate(id);
                    },
                    this.timeout
                    );
        },

        end: function (id) {
            var screen = window.flickerfreeScreen.screens[id];

            if (!empty(screen) && (screen.timestamp + this.timeout) >= screen.timestampActual) {
                var timer = this.timers[id];
                timer.inUpdate = false;

                clearTimeout(timer.timeoutHandler);
                this.removeShadow(id);
                this.fadeSpeed(id, 2000);
            }
        },

        validate: function (id) {
            var screen = window.flickerfreeScreen.screens[id];

            if (!empty(screen) && (screen.timestamp + this.timeout) < screen.timestampActual) {
                this.createShadow(id);
                this.refresh(id);
            } else {
                this.end(id);
            }
        },

        createShadow: function (id) {
            var timer = this.timers[id];

            if (!empty(timer) && !timer.isShadowed) {
                var obj = $('#flickerfreescreen_' + id),
                        item = window.flickerfreeScreenShadow.findScreenItem(obj);

                if (empty(item)) {
                    return;
                }

                // don't show shadow if image not loaded first time with the page
                if (item.prop('nodeName') === 'IMG' && !timer.ready && typeof item.get(0).complete === 'boolean') {
                    if (!item.get(0).complete) {
                        return;
                    } else {
                        timer.ready = true;
                    }
                }

                // create shadow
                if (obj.find('.shadow').length === 0) {
                    item.css({position: 'relative', zIndex: 2});

                    obj.append($('<div>', {'class': 'shadow'})
                            .html('&nbsp;')
                            .css({
                                top: item.position().top,
                                left: item.position().left,
                                width: item.width(),
                                height: item.height(),
                                position: 'absolute',
                                zIndex: 1
                            })
                            );

                    // fade screen
                    var itemNode = obj.find(item.prop('nodeName'));
                    if (!empty(itemNode)) {
                        itemNode = (itemNode.length > 0) ? $(itemNode[0]) : itemNode;
                        itemNode.fadeTo(timer.fadeSpeed, 0.6);
                    }

                    // show loading indicator..
                    obj.append($('<div>', {'class': 'preloader'})
                            .css({
                                width: '24px',
                                height: '24px',
                                position: 'absolute',
                                zIndex: 3,
                                top: item.position().top + Math.round(item.height() / 2) - 12,
                                left: item.position().left + Math.round(item.width() / 2) - 12
                            })
                            );

                    timer.isShadowed = true;
                }
            }
        },

        removeShadow: function (id) {
            var timer = this.timers[id];

            if (!empty(timer) && timer.isShadowed) {
                var obj = $('#flickerfreescreen_' + id),
                        item = window.flickerfreeScreenShadow.findScreenItem(obj);
                if (empty(item)) {
                    return;
                }

                obj.find('.preloader').remove();
                obj.find('.shadow').remove();
                obj.find(item.prop('nodeName')).fadeTo(0, 1);

                timer.isShadowed = false;
            }
        },

        moveShadows: function () {
            $('.flickerfreescreen').each(function () {
                var obj = $(this),
                        item = window.flickerfreeScreenShadow.findScreenItem(obj);

                if (empty(item)) {
                    return;
                }

                // shadow
                var shadows = obj.find('.shadow');

                if (shadows.length > 0) {
                    shadows.css({
                        top: item.position().top,
                        left: item.position().left,
                        width: item.width(),
                        height: item.height()
                    });
                }

                // loading indicator
                var preloader = obj.find('.preloader');

                if (preloader.length > 0) {
                    preloader.css({
                        top: item.position().top + Math.round(item.height() / 2) - 12,
                        left: item.position().left + Math.round(item.width() / 2) - 12
                    });
                }
            });
        },

        findScreenItem: function (obj) {
            var item = obj.children().eq(0),
                    tag;

            if (!empty(item)) {
                tag = item.prop('nodeName');

                if (tag === 'MAP') {
                    item = obj.children().eq(1);
                    tag = item.prop('nodeName');
                }

                if (tag === 'DIV') {
                    var imgItem = item.find('img');

                    if (imgItem.length > 0) {
                        item = $(imgItem[0]);
                        tag = 'IMG';
                    }
                }

                if (tag === 'TABLE' || tag === 'DIV' || tag === 'IMG') {
                    return item;
                } else {
                    item = item.find('img');

                    return (item.length > 0) ? $(item[0]) : null;
                }
            } else {
                return null;
            }
        },

        isShadowed: function (id, isShadowed) {
            var timer = this.timers[id];

            if (!empty(timer)) {
                if (typeof isShadowed !== 'undefined') {
                    this.timers[id].isShadowed = isShadowed;
                }

                return this.timers[id].isShadowed;
            }

            return false;
        },

        fadeSpeed: function (id, fadeSpeed) {
            var timer = this.timers[id];

            if (!empty(timer)) {
                if (typeof fadeSpeed !== 'undefined') {
                    this.timers[id].fadeSpeed = fadeSpeed;
                }

                return this.timers[id].fadeSpeed;
            }

            return 0;
        },

        cleanAll: function () {
            for (var id in this.timers) {
                var timer = this.timers[id];

                if (!empty(timer.timeoutHandler)) {
                    clearTimeout(timer.timeoutHandler);
                }
            }

            this.timers = [];
        }
    };

    $(window).resize(function () {
        window.flickerfreeScreenShadow.moveShadows();
    });

    var theme = {
        "graphthemeid": '1',
        "theme": 'blue-theme',
        "backgroundcolor": 'FFFFFF',
        "graphcolor": 'FFFFFF',
        "gridcolor": 'CCD5D9',
        "maingridcolor": 'ACBBC2',
        "gridbordercolor": 'ACBBC2',
        "textcolor": '1F2C33',
        "highlightcolor": 'E33734',
        "leftpercentilecolor": '429E47',
        "rightpercentilecolor": 'E33734',
        "nonworktimecolor": 'EBEBEB'
    };
    var registry = {};
    var counter = 0;

    /*
     ** Zabbix
     ** Copyright (C) 2001-2017 Zabbix SIA
     **
     ** This program is free software; you can redistribute it and/or modify
     ** it under the terms of the GNU General Public License as published by
     ** the Free Software Foundation; either version 2 of the License, or
     ** (at your option) any later version.
     **
     ** This program is distributed in the hope that it will be useful,
     ** but WITHOUT ANY WARRANTY; without even the implied warranty of
     ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
     ** GNU General Public License for more details.
     **
     ** You should have received a copy of the GNU General Public License
     ** along with this program; if not, write to the Free Software
     ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
     **/
    var agt = navigator.userAgent.toLowerCase(),
            IE6 = (agt.indexOf('msie 6.0') !== -1),
            IE7 = (agt.indexOf('msie 7.0') !== -1),
            IE8 = (agt.indexOf('msie 8.0') !== -1),
            IE9 = (agt.indexOf('msie 9.0') !== -1),
            IE10 = (agt.indexOf('msie 10.0') !== -1),
            IE11 = !!agt.match(/trident\/.*rv:11/),
            IE = (IE6 || IE7 || IE8 || IE9 || IE10 || IE11),
            ED = (agt.indexOf('edge') !== -1),
            CR = (agt.indexOf('chrome') !== -1 && !ED),
            SF = (agt.indexOf('safari') !== -1 && !CR),
            KQ = (agt.indexOf('konqueror') && agt.indexOf('khtml') !== -1 && agt.indexOf('applewebkit') === -1),
            GK = (agt.indexOf('gecko') !== -1);

    /*
     ** Zabbix
     ** Copyright (C) 2001-2017 Zabbix SIA
     **
     ** This program is free software; you can redistribute it and/or modify
     ** it under the terms of the GNU General Public License as published by
     ** the Free Software Foundation; either version 2 of the License, or
     ** (at your option) any later version.
     **
     ** This program is distributed in the hope that it will be useful,
     ** but WITHOUT ANY WARRANTY; without even the implied warranty of
     ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
     ** GNU General Public License for more details.
     **
     ** You should have received a copy of the GNU General Public License
     ** along with this program; if not, write to the Free Software
     ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
     **/


    /**
     * SVGCanvas class.
     *
     * Implements basic functionality needed to render SVG from JS.
     *
     * @param {object}	options				Canvas options.
     * @param {number}	options.width		Canvas width (width attribute of a SVG image).
     * @param {number}	options.height		Canvas height (height attribute of a SVG image).
     * @param {boolean}	options.mask		Masking option for textarea elements (@see SVGCanvas.prototype.createTextarea)
     * @param {boolean}	shadowBuffer		Shadow buffer (double buffering) support. If set to true, additional hidden
     *										group element is created within SVG.
     */
    function SVGCanvas(options, shadowBuffer) {
        this.options = options;
        options.theme = options.theme || theme;
        this.id = 0;
        this.elements = [];
        this.textPadding = 5;
        this.maskColor = '#3d3d3d';
        this.mask = false;

        if (typeof options.mask !== 'undefined') {
            this.mask = (options.mask === true);
        }
        if (typeof options.useViewBox !== 'boolean') {
            options.useViewBox = false;
        }

        this.buffer = null;

        var svg_options = options.useViewBox
                ? {
                    'viewBox': '0 0 ' + options.width + ' ' + options.height,
                    'width': '100%',
                    'height': '100%',
                    'style': 'max-width: ' + options.width + 'px; max-height: ' + options.height + 'px;'
                }
        : {
            'width': options.width,
            'height': options.height
        };

        this.root = this.createElement('svg', svg_options, null);
        if (shadowBuffer === true) {
            this.buffer = this.root.add('g', {
                'class': 'shadow-buffer',
                style: 'visibility: hidden;'
            });
        }
    }
    registry['SVGCanvas'] = SVGCanvas;

    // Predefined namespaces for SVG as key => value
    SVGCanvas.NAMESPACES = {
        xlink: 'http://www.w3.org/1999/xlink'
    };

    /**
     * Generate unique id.
     * Id is unique within page context.
     *
     * @return {number} Unique id.
     */
    SVGCanvas.getUniqueId = function () {
        if (typeof SVGCanvas.uniqueId === 'undefined') {
            SVGCanvas.uniqueId = 0;
        }

        return SVGCanvas.uniqueId++;
    };

    /**
     * Create new SVG element.
     * Additional workaround is added to implement textarea element as a text element with a set of tspan subelements.
     *
     * @param {string}     type             Element type (SVG tag).
     * @param {object}     attributes       Element attributes (SVG tag attributes) as key => value pairs.
     * @param {SVGElement} parent           Parent element if any (or null if none).
     * @param {mixed}      content          Element textContent of a set of subelements.
     *
     * @return {SVGElement} Created element.
     */
    SVGCanvas.prototype.createElement = function (type, attributes, parent, content) {
        var element;

        if (type.toLowerCase() === 'textarea') {
            var textarea = new SVGTextArea(this);
            element = textarea.create(attributes, parent, content);
        } else {
            element = new SVGElement(this, type, attributes, parent, content);
            this.elements.push(element);
        }

        return element;
    };

    /**
     * Get elements by specified attributes.
     *
     * SVG elements with specified attributes are returned as array of SVGElement (if any).
     *
     * @return {array} Elements that match specified attributes.
     */
    SVGCanvas.prototype.getElementsByAttributes = function (attributes) {
        var names = Object.keys(attributes),
                elements = this.elements.filter(function (item) {
                    for (var i = 0; i < names.length; i++) {
                        if (item.attributes[names[i]] !== attributes[names[i]]) {
                            return false;
                        }
                    }

                    return true;
                });

        return elements;
    };

    /**
     * Add element to the SVG root element (svg tag).
     *
     * @return {SVGElement} Created element.
     */
    SVGCanvas.prototype.add = function (type, attributes, content) {
        return this.root.add(type, attributes, content);
    };

    /**
     * Attach SVG element to the specified container in DOM.
     *
     * @param {object}     container       DOM node.
     */
    SVGCanvas.prototype.render = function (container) {
        if (this.root.element.parentNode) {
            this.root.element.parentNode.removeChild(this.root.element);
        }

        container.appendChild(this.root.element);
    };

    /**
     * Resize canvas.
     *
     * @param {number}     width       New width.
     * @param {number}     height      New height.
     *
     * @return {boolean} true if size is changed and false if size is the same as previous.
     */
    SVGCanvas.prototype.resize = function (width, height) {
        if (this.options.width !== width || this.options.height !== height) {
            this.options.width = width;
            this.options.height = height;
            this.root.update({'width': width, 'height': height});

            return true;
        }

        return false;
    };

    /**
     * SVGTextArea class.
     *
     * Implements textarea (multiline text) for svg.
     *
     * @param {object}     canvas       Instance of SVGCanvas.
     *
     */
    function SVGTextArea(canvas) {
        this.canvas = canvas;
        this.element = null;
    }
    registry['SVGTextArea'] = SVGTextArea;

    /**
     * Parse text line and extract links as <a> elements.
     *
     * @param {string} text		Text line to be parsed.
     *
     * @return {mixed}			Parsed text as {array} if links are present or as {string} if there are no links in text.
     */
    SVGTextArea.parseLinks = function (text) {
        var index,
                offset = 0,
                link,
                parts = [];

        while ((index = text.search(/((ftp|file|https?):\/\/[^\s]+)/i)) !== -1) {
            if (offset !== index) {
                parts.push(text.substring(offset, index));
            }

            text = text.substring(index);
            index = text.search(/\s/);

            if (index === -1) {
                index = text.length;
            }

            link = text.substring(0, index);
            text = text.substring(index);
            offset = 0;
            parts.push({
                type: 'a',
                attributes: {
                    href: link,
                    onclick: 'window.location = ' + JSON.stringify(link) + '; return false;' // Workaround for Safari.
                },
                content: link
            });
        }

        if (text !== '') {
            if (parts.length !== 0) {
                parts.push(text);
            } else {
                parts = text;
            }
        }

        return parts;
    };

    /**
     * Wrap text line to the specified width.
     *
     * @param {string} line		Text line to be wrapped.
     *
     * @return {array}			Wrapped line as {array} of strings.
     */
    SVGTextArea.prototype.wrapLine = function (line) {
        if (this.canvas.buffer === null || typeof this.clip === 'undefined') {
            // No text wrapping without shadow buffer of clipping object.
            return [line];
        }

        var max_width = this.clip.attributes.width,
                current;

        if (typeof max_width === 'undefined' && typeof this.clip.attributes.rx !== 'undefined') {
            max_width = parseInt(this.clip.attributes.rx * 2, 10);
        }

        max_width -= this.canvas.textPadding * 2;

        if (typeof this.canvas.wrapper === 'undefined') {
            this.canvas.wrapper = {
                text: this.canvas.buffer.add('text', this.attributes),
                node: document.createTextNode('')
            };

            this.canvas.wrapper.text.element.appendChild(this.canvas.wrapper.node);
        } else {
            this.canvas.wrapper.text.update(this.attributes);
        }

        var text = this.canvas.wrapper.text.element,
                node = this.canvas.wrapper.node,
                size,
                wrapped = [];

        node.textContent = line;
        size = text.getBBox();

        // Check length of the line in pixels.
        if (Math.ceil(size.width) > max_width) {
            var words = line.split(' ');
            current = [];

            while (words.length > 0) {
                current.push(words.shift());
                node.textContent = current.join(' ');
                size = text.getBBox();

                if (Math.ceil(size.width) > max_width) {
                    if (current.length > 1) {
                        words.unshift(current.pop());
                        wrapped.push(current.join(' '));
                        current = [];
                    } else {
                        // Word is too long to fit the line.
                        wrapped.push(current.pop());
                    }
                }
            }

            if (current.length > 0) {
                wrapped.push(current.join(' '));
            }
        } else {
            wrapped.push(line);
        }

        return wrapped;
    };

    /**
     * Get horizontal offset (position in pixels) of text anchor.
     *
     * @return {numeric}		Horizontal offset in pixels.
     */
    SVGTextArea.prototype.getHorizontalOffset = function () {
        switch (this.anchor.horizontal) {
            case 'center':
                return Math.floor(this.width / 2);

            case 'right':
                return this.width;
        }

        return 0;
    };

    /**
     * Get text-anchor attribute value from horizontal anchor value.
     *
     * @return {string}		Value of text-anchor attribute.
     */
    SVGTextArea.prototype.getHorizontalAnchor = function () {
        var mapping = {
            left: 'start',
            center: 'middle',
            right: 'end'
        };

        if (typeof mapping[this.anchor.horizontal] === 'string') {
            return mapping[this.anchor.horizontal];
        }

        return mapping.left;
    };

    /**
     * Parse content, get the lines, perform line wrapping and link parsing.
     *
     * @param {mixed}	content		Text contents or array of line objects.
     * @param {boolean} parse_links	Set to true if link parsing should be performed.
     *
     * @return {numeric}		Horizontal offset in pixels.
     */
    SVGTextArea.prototype.parseContent = function (content, parse_links) {
        var skip = 0.9,
                anchor = this.getHorizontalAnchor();

        this.lines = [];

        if (typeof content === 'string') {
            var items = [];

            content.split("\n").forEach(function (line) {
                items.push({
                    content: line,
                    attributes: {}
                });
            });

            content = items;
        }

        content.forEach(function (line) {
            if (line.content.trim() !== '') {
                var content = line.content.replace(/[\r\n]/g, '');

                this.wrapLine(content).forEach(function (wrapped) {
                    if (parse_links === true) {
                        wrapped = SVGTextArea.parseLinks(wrapped);
                    }

                    this.lines.push({
                        type: 'tspan',
                        attributes: SVGElement.mergeAttributes({
                            x: this.offset,
                            dy: skip + 'em',
                            'text-anchor': anchor
                        }, line.attributes),
                        content: wrapped
                    });

                    skip = 1.2;
                }, this);
            } else {
                skip += 1.2;
            }
        }, this);
    };

    /**
     * Align text position based on horizontal and vertical anchor values.
     */
    SVGTextArea.prototype.alignToAnchor = function () {
        if (typeof this.anchor !== 'object') {
            this.anchor = {
                horizontal: 'left'
            };
        }

        this.x -= this.getHorizontalOffset();

        switch (this.anchor.vertical) {
            case 'middle':
                this.y -= Math.floor(this.height / 2);
                break;

            case 'bottom':
                this.y -= this.height;
                break;
        }
    };

    /**
     * Create clipping object to clip (and/or mask) text outside the specified shape.
     */
    SVGTextArea.prototype.createClipping = function () {
        if (typeof this.clip !== 'undefined') {
            var offset = this.getHorizontalOffset();
            // Clipping shape should be applied to the text. Clipping mode (clip or mask) depends on mask attribute.

            if (typeof this.clip.attributes.x !== 'undefined' && typeof this.clip.attributes.y !== 'undefined') {
                this.clip.attributes.x -= (this.x + offset);
                this.clip.attributes.y -= this.y;
            } else if (typeof this.clip.attributes.cx !== 'undefined' && typeof this.clip.attributes.cy !== 'undefined') {
                this.clip.attributes.cx -= (this.x + offset);
                this.clip.attributes.cy -= this.y;
            }

            var unique_id = SVGCanvas.getUniqueId();

            if (this.canvas.mask) {
                this.clip.attributes.fill = '#ffffff';
                this.element.add('mask', {
                    id: 'mask-' + unique_id
                }, [{
                        type: 'rect',
                        attributes: {
                            x: -offset,
                            y: 0,
                            'width': this.width,
                            'height': this.height,
                            fill: this.canvas.maskColor
                        }
                    },
                    this.clip
                ]);

                this.text.element.setAttribute('mask', 'url(#mask-' + unique_id + ')');
            } else {
                this.element.add('clipPath', {
                    id: 'clip-' + unique_id
                }, [this.clip]);

                this.text.element.setAttribute('clip-path', 'url(#clip-' + unique_id + ')');
            }
        }
    };

    /**
     * Create new textarea element.
     *
     * Textarea element has poor support in supported browsers so following workaround is used. Textarea element is a text
     * element with a set of tspan subelements and additional logic for text background and masking / clipping.
     *
     * @param {string}		type							Element type (SVG tag).
     * @param {object}		attributes						Element attributes (SVG tag attributes).
     * @param {number}		attributes.x					Element position on x axis.
     * @param {number}		attributes.y					Element position on y axis.
     * @param {object}		attributes.anchor				Anchor used for text placement.
     * @param {string}		attributes.anchor.horizontal	Horizontal anchor used for text placement.
     * @param {string}		attributes.anchor.vertical		Vertical anchor used for text placement.
     * @param {object}		attributes.background			Attributes of rectangle placed behind text (text background).
     * @param {object}		attributes.clip					SVG element used for clipping or masking (depends on canvas mask option).
     * @param {SVGElement}	parent							Parent element if any (or null if none).
     * @param {mixed}		content							Element textContent of a set of subelements.
     *
     * @return {SVGElement} Created element.
     */
    SVGTextArea.prototype.create = function (attributes, parent, content) {
        if (typeof content === 'string' && content.trim() === '') {
            return null;
        }

        if (Array.isArray(content)) {
            var i;

            for (i = 0; i < content.length; i++) {
                if (content[i].content.trim() !== '') {
                    break;
                }
            }

            if (i === content.length) {
                return null;
            }
        }

        ['x', 'y', 'anchor', 'background', 'clip'].forEach(function (key) {
            this[key] = attributes[key];
        }, this);

        this.offset = 0;
        this.element = this.canvas.createElement('g', null, parent);

        var parse_links = attributes['parse-links'],
                size;

        ['x', 'y', 'anchor', 'background', 'clip', 'parse-links'].forEach(function (key) {
            delete attributes[key];
        });

        this.attributes = attributes;

        if (typeof this.background === 'object') {
            this.background = this.element.add('rect', this.background);
            this.x -= this.canvas.textPadding;
            this.y -= this.canvas.textPadding;
            this.offset = this.canvas.textPadding;
        } else {
            this.background = null;
        }

        this.parseContent(content, parse_links);
        this.text = this.element.add('text', attributes, this.lines);

        size = this.text.element.getBBox();
        this.width = Math.ceil(size.width);
        this.height = Math.ceil(size.height + size.y);

        // Workaround for IE/EDGE for proper text height calculation.
        if ((IE || ED) && this.lines.length > 0
                && typeof attributes['font-size'] !== 'undefined' && parseInt(attributes['font-size']) > 16) {
            this.height = Math.ceil(this.lines.length * parseInt(attributes['font-size']) * 1.2);
        }

        this.alignToAnchor();

        if (this.background !== null) {
            this.background.update({
                width: this.width + (this.canvas.textPadding * 2),
                height: this.height + (this.canvas.textPadding * 2)
            });
        }

        this.createClipping();

        this.text.element.setAttribute('transform', 'translate(' + this.getHorizontalOffset() + ' ' + this.offset + ')');
        this.element.element.setAttribute('transform', 'translate(' + this.x + ' ' + this.y + ')');

        return this.element;
    };

    /**
     * ImageCache class.
     *
     * Implements basic functionality needed to preload images, get image attributes and avoid flickering.
     */
    function ImageCache() {
        this.lock = 0;
        this.images = {};
        this.context = null;
        this.callback = null;
        this.queue = [];
    }
    registry['ImageCache'] = ImageCache;

    /**
     * Invoke callback (if any), update image preload task queue.
     */
    ImageCache.prototype.invokeCallback = function () {
        if (typeof this.callback === 'function') {
            this.callback.call(this.context);
        }

        // Preloads next image list if any.
        var task = this.queue.pop();

        if (typeof task !== 'undefined') {
            this.preload(task.urls, task.callback, task.context);
        }
    };

    /**
     * Handle image processing event (loaded or error).
     */
    ImageCache.prototype.handleCallback = function () {
        this.lock--;

        // If all images are loaded (error is treated as "loaded"), invoke callback.
        if (this.lock === 0) {
            this.invokeCallback();
        }
    };

    /**
     * Callback for sucessful image load.
     *
     * @param {string}     id       Image id.
     * @param {object}     image    Loaded image.
     */
    ImageCache.prototype.onImageLoaded = function (id, image) {
        this.images[id] = image;
        this.handleCallback();
    };

    /**
     * Callback for image loading errors.
     *
     * @param {string}     id       Image id.
     */
    ImageCache.prototype.onImageError = function (id) {
        this.images[id] = null;
        this.handleCallback();
    };

    /**
     * Preload images.
     *
     * @param {object}		urls		Urls of images to be preloaded (urls are provided in key=>value format).
     * @param {function}	callback	Callback to be called when loading is finished. Can be null if no callback is needed.
     * @param {object}		context		Context of a callback. (@see first argument of Function.prototype.apply)
     *
     * @return {boolean} true if preloader started loading images and false if preloader is busy.
     */
    ImageCache.prototype.preload = function (urls, callback, context) {
        // If preloader is busy, new preloading task is pushed to queue.
        if (this.lock !== 0) {
            this.queue.push({
                'urls': urls,
                'callback': callback,
                'context': context
            });

            return false;
        }

        this.context = context;
        this.callback = callback;

        var images = 0;
        var object = this;

        Object.keys(urls).forEach(function (key) {
            var url = urls[key];

            if (typeof url !== 'string') {
                object.onImageError.call(object, key);

                return;
            }

            if (typeof object.images[key] !== 'undefined') {
                // Image is pre-loaded already.
                return true;
            }

            var image = new Image();

            image.onload = function () {
                object.onImageLoaded.call(object, key, image);
            };

            image.onerror = function () {
                object.onImageError.call(object, key);
            };

            image.src = url;

            object.lock++;
            images++;
        });

        if (images === 0) {
            this.invokeCallback();
        }

        return true;
    };

    /**
     * SVGElement class.
     *
     * Implements basic functionality needed to create SVG elements.
     *
     * @see SVGCanvas.prototype.createElement
     *
     * @param {SVGCanvas}  renderer    SVGCanvas used to render elements.
     * @param {string}     type        Type of SVG element.
     * @param {object}     attributes  Element attributes (SVG tag attributes) as key => value pairs.
     * @param {SVGElement} parent      Parent element if any (or null if none).
     * @param {mixed}      content     Element textContent of a set of subelements.
     */
    function SVGElement(renderer, type, attributes, parent, content) {
        this.id = renderer.id++;
        this.type = type;
        this.attributes = attributes;
        this.content = content;
        this.canvas = renderer;
        this.parent = parent;
        this.items = [];
        this.element = null;
        this.invalid = false;

        if (type !== null) {
            this.create();
        }
    }
    registry['SVGElement'] = SVGElement;

    /**
     * Add clild SVG element.
     *
     * @see SVGCanvas.prototype.createElement
     *
     * @param {mixed}      type        Type of SVG element or array of objects containing type, attribute and content fields.
     * @param {object}     attributes  Element attributes (SVG tag attributes) as key => value pairs.
     * @param {mixed}      content     Element textContent of a set of subelements.
     *
     * @return {mixed} SVGElement created or array of SVGElement is type was Array.
     */
    SVGElement.prototype.add = function (type, attributes, content) {
        // Multiple items to add.
        if (Array.isArray(type)) {
            var items = [];

            type.forEach(function (element) {
                if (typeof element !== 'object' || typeof element.type !== 'string') {
                    throw 'Invalid element configuration!';
                }

                items.push(this.add(element.type, element.attributes, element.content));
            }, this);

            return items;
        }

        if (typeof attributes === 'undefined' || attributes === null) {
            attributes = {};
        }

        var element = this.canvas.createElement(type, attributes, this, content);

        if (type.toLowerCase() !== 'textarea') {
            this.items.push(element);
        }

        return element;
    };

    /**
     * Remove all children elements.
     *
     * @return {SVGElement}
     */
    SVGElement.prototype.clear = function () {
        var items = this.items;

        items.forEach(function (item) {
            item.remove();
        });

        this.items = [];

        return this;
    };

    /**
     * Update attributes of SVG element.
     *
     * @param {object} attributes		New element attributes (SVG tag attributes) as key => value pairs.
     *
     * @return {SVGElement}
     */
    SVGElement.prototype.update = function (attributes) {
        Object.keys(attributes).forEach(function (name) {
            var attribute = name.split(':');

            if (attribute.length === 1) {
                this.element.setAttributeNS(null, name, attributes[name]);
            } else if (attribute.length === 2 && typeof SVGCanvas.NAMESPACES[attribute[0]] !== 'undefined') {
                this.element.setAttributeNS(SVGCanvas.NAMESPACES[attribute[0]], name, attributes[name]);
            }
        }, this);

        return this;
    };

    /**
     * Moves element from one parent to another.
     *
     * @param {object} target		New parent element.
     *
     * @return {SVGElement}
     */
    SVGElement.prototype.moveTo = function (target) {
        this.parent.items = this.parent.items.filter(function (item) {
            return item.id !== this.id;
        }, this);

        this.parent = target;
        this.parent.items.push(this);
        target.element.appendChild(this.element);

        return this;
    };

    /**
     * Mark element as invalid (flag used to force redraw of element).
     *
     * @return {SVGElement}
     */
    SVGElement.prototype.invalidate = function () {
        this.invalid = true;

        return this;
    };

    /**
     * Remove element from parent and from DOM.
     *
     * @return {SVGElement}
     */
    SVGElement.prototype.remove = function () {
        this.clear();

        if (this.element !== null) {
            // Workaround for IE as .remove() does not work in IE.
            if (typeof this.element.remove !== 'function') {
                if (typeof this.element.parentNode !== 'undefined') {
                    this.element.parentNode.removeChild(this.element);
                }
            } else {
                this.element.remove();
            }
            this.element = null;
        }

        if (this.parent !== null && typeof this.parent.items !== 'undefined') {
            this.parent.items = this.parent.items.filter(function (item) {
                return item.id !== this.id;
            }, this);
        }

        return this;
    };

    /**
     * Replace existing DOM element with a new one.
     *
     * @param {object} target		New DOM element.
     *
     * @return {SVGElement}
     */
    SVGElement.prototype.replace = function (target) {
        if (this.element !== null && this.invalid === false) {
            this.element.parentNode.insertBefore(target.element, this.element);
        }

        this.remove();

        Object.keys(target).forEach(function (key) {
            this[key] = target[key];
        }, this);

        return this;
    };

    /**
     * Create SVG DOM element.
     *
     * @return {object} DOM element.
     */
    SVGElement.prototype.create = function () {
        var element = (this.type !== '')
                ? document.createElementNS('http://www.w3.org/2000/svg', this.type)
                : document.createTextNode(this.content);

        this.remove();
        this.element = element;

        if (this.type !== '' && this.attributes !== null) {
            this.update(this.attributes);

            if (Array.isArray(this.content)) {
                this.content.forEach(function (element) {
                    if (typeof element === 'string') {
                        // Treat element as a text node.
                        element = {
                            type: '',
                            attributes: null,
                            content: element
                        };
                    }

                    if (typeof element !== 'object' || typeof element.type !== 'string') {
                        throw 'Invalid element configuration!';
                    }

                    this.add(element.type, element.attributes, element.content);
                }, this);

                this.content = null;
            } else if ((/string|number|boolean/).test(typeof this.content)) {
                element.textContent = this.content;
            }
        }

        if (this.parent !== null && this.parent.element !== null) {
            this.parent.element.appendChild(element);
        }

        return element;
    };

    /**
     * Merge source and target attributes.  If both source and attributes contain the same set of keys, values from
     * attributes are used.
     *
     * @param {object}	source			Source object attributes.
     * @param {object}	attributes		New object attributes.
     *
     * @return {object}					Merged set of attributes.
     */
    SVGElement.mergeAttributes = function (source, attributes) {
        var merged = {};

        if (typeof source === 'object') {
            Object.keys(source).forEach(function (key) {
                merged[key] = source[key];
            });
        }

        if (typeof attributes === 'object') {
            Object.keys(attributes).forEach(function (key) {
                merged[key] = attributes[key];
            });
        }

        return merged;
    };

    /*
     ** Zabbix
     ** Copyright (C) 2001-2017 Zabbix SIA
     **
     ** This program is free software; you can redistribute it and/or modify
     ** it under the terms of the GNU General Public License as published by
     ** the Free Software Foundation; either version 2 of the License, or
     ** (at your option) any later version.
     **
     ** This program is distributed in the hope that it will be useful,
     ** but WITHOUT ANY WARRANTY; without even the implied warranty of
     ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
     ** GNU General Public License for more details.
     **
     ** You should have received a copy of the GNU General Public License
     ** along with this program; if not, write to the Free Software
     ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
     **/


    /**
     * SVGMap class.
     *
     * Implements vector map rendering functionality.
     */
    function SVGMap(options, server) {
        var container,
                layers;

        this.layers = {};
        this.options = options;
        this.options.theme = this.options.theme || theme;
        this.elements = {};
        this.shapes = {};
        this.links = {};
        this.background = null;
        this.container = null;
        //this.imageUrl = AJS.contextPath() + '/download/resources/com.mesilat.zabbix-plugin/images/';
        this.imageUrl = AJS.contextPath() + '/plugins/servlet/zabbix-static-image?server=' + server + '&image=';
        this.imageCache = new ImageCache();
        this.canvas = new SVGCanvas(options.canvas, true);

        // Extra group for font styles.
        container = this.canvas.add('g', {
            'class': 'map-container',
            'font-family': SVGMap.FONTS[9],
            'font-size': '10px'
        });

        layers = container.add([
            //  Background.
            {
                type: 'g',
                attributes: {
                    'class': 'map-background',
                    fill: '#' + options.theme.backgroundcolor
                }
            },
            // Grid.
            {
                type: 'g',
                attributes: {
                    'class': 'map-grid',
                    stroke: '#' + options.theme.gridcolor,
                    fill: '#' + options.theme.gridcolor,
                    'stroke-width': '1',
                    'stroke-dasharray': '4,4',
                    'shape-rendering': 'crispEdges'
                }
            },
            // Custom shapes.
            {
                type: 'g',
                attributes: {
                    'class': 'map-shapes'
                }
            },
            // Highlights of elements.
            {
                type: 'g',
                attributes: {
                    'class': 'map-highlights'
                }
            },
            // Links.
            {
                type: 'g',
                attributes: {
                    'class': 'map-links'
                }
            },
            // Elements.
            {
                type: 'g',
                attributes: {
                    'class': 'map-elements'
                }
            },
            // Marks (timestamp and homepage).
            {
                type: 'g',
                attributes: {
                    'class': 'map-marks',
                    fill: 'rgba(150,150,150,0.75)',
                    'font-size': '8px',
                    'shape-rendering': 'crispEdges'
                },

                content: [
                    {
                        type: 'text',
                        attributes: {
                            'class': 'map-timestamp',
                            x: options.canvas.width - 107,
                            y: options.canvas.height - 6
                        }
                    },
                    {
                        type: 'text',
                        attributes: {
                            'class': 'map-homepage',
                            x: options.canvas.width,
                            y: options.canvas.height - 50,
                            transform: 'rotate(270 ' + (options.canvas.width) + ', ' + (options.canvas.height - 50) + ')'
                        },
                        content: options.homepage
                    }
                ]
            }
        ]);

        ['background', 'grid', 'shapes', 'highlights', 'links', 'elements', 'marks'].forEach(function (attribute, index) {
            this.layers[attribute] = layers[index];
        }, this);

        this.layers.background.add('rect', {
            x: 0,
            y: 0,
            width: this.options.canvas.width,
            height: this.options.canvas.height
        });

        // Render goes first as it is needed for getBBox to work.
        if (this.options.container) {
            this.render(this.options.container);
        }

        ['timestamp', 'homepage'].forEach(function (attribute) {
            var elements = this.canvas.getElementsByAttributes({'class': 'map-' + attribute});

            if (elements.length === 1) {
                this[attribute] = elements[0];
            } else {
                throw attribute + " element is missing";
            }
        }, this);

        this.update(this.options);
    }
    registry['SVGMap'] = SVGMap;

// Predefined list of fonts for maps.
    SVGMap.FONTS = [
        'Georgia, serif',
        '"Palatino Linotype", "Book Antiqua", Palatino, serif',
        '"Times New Roman", Times, serif',
        'Arial, Helvetica, sans-serif',
        '"Arial Black", Gadget, sans-serif',
        '"Comic Sans MS", cursive, sans-serif',
        'Impact, Charcoal, sans-serif',
        '"Lucida Sans Unicode", "Lucida Grande", sans-serif',
        'Tahoma, Geneva, sans-serif',
        '"Trebuchet MS", Helvetica, sans-serif',
        'Verdana, Geneva, sans-serif',
        '"Courier New", Courier, monospace',
        '"Lucida Console", Monaco, monospace'
    ];

// Predefined border types (@see dash-array of SVG) for maps.
    SVGMap.BORDER_TYPES = {
        '0': '',
        '1': 'none',
        '2': '1,2',
        '3': '4,4'
    };

    /**
     * Convert array of objects to hashmap (object).
     *
     * @param {array}     array   Array of objects.
     * @param {string}    key     Object field used to identify object.
     *
     * @return {object} Hashmap.
     */
    SVGMap.toHashmap = function (array, key) {
        var hashmap = {};

        array.forEach(function (item) {
            if (typeof item !== 'object' || typeof item[key] === 'undefined') {
                // Skip elements that are not objects.
                return;
            }

            hashmap[item[key]] = item;
        });

        return hashmap;
    };

    /**
     * Get image url.
     *
     * @param {number|string}    id     Image id.
     *
     * @return {string} Image url.
     */
    SVGMap.prototype.getImageUrl = function (id) {
        return this.imageUrl + id; // + '.png';
    };

    /**
     * Get image from image cache.
     *
     * @param {number|string}    id     Image id.
     *
     * @return {object} Image object or null if image object is not present in cache.
     */
    SVGMap.prototype.getImage = function (id) {
        if (typeof id !== 'undefined' && typeof this.imageCache.images[id] !== 'undefined') {
            return this.imageCache.images[id];
        }

        return null;
    };

    /**
     * Update background image.
     *
     * @param {string}    background     Background image id.
     */
    SVGMap.prototype.updateBackground = function (background) {
        var element = null;

        if (background && background !== '0') {
            if (this.background !== null && background === this.options.background) {
                // Background was not changed.
                return;
            }

            var image = this.getImage(background);

            element = this.layers.background.add('image', {
                x: 0,
                y: 0,
                width: image.naturalWidth,
                height: image.naturalHeight,
                'xlink:href': this.getImageUrl(background)
            });
        }

        if (this.background !== null) {
            this.background.remove();
        }

        this.background = element;
    };

    /**
     * Set grid size.
     *
     * @param {number}    size     Grid size. Setting grid size to 0 turns of the grid.
     */
    SVGMap.prototype.setGrid = function (size) {
        this.layers.grid.clear();

        if (size === 0) {
            return;
        }

        for (var x = size; x < this.options.canvas.width; x += size) {
            this.layers.grid.add('line', {
                'x1': x,
                'y1': 0,
                'x2': x,
                'y2': this.options.canvas.height
            });

            this.layers.grid.add('text', {
                'x': x + 3,
                'y': 9 + 3,
                'stroke-width': 0
            }, x);
        }

        for (var y = size; y < this.options.canvas.height; y += size) {
            this.layers.grid.add('line', {
                'x1': 0,
                'y1': y,
                'x2': this.options.canvas.width,
                'y2': y
            });

            this.layers.grid.add('text', {
                'x': 3,
                'y': y + 12,
                'stroke-width': 0
            }, y);
        }

        this.layers.grid.add('text', {
            'x': 2,
            'y': 12,
            'stroke-width': 0
        }, 'Y X:');
    };

    /**
     * Compare objects.  * Used to compare map object attributes to determine if attributes were changed.
     *
     * @param {object} source	Object to be compared.
     * @param {object} target	Object to be compared with.
     *
     * @return {boolean}		True if objects attributes are different, false if object attributes are the same.
     */
    SVGMap.isChanged = function (source, target) {
        if (typeof source !== 'object' || source === null) {
            return true;
        }

        var keys = Object.keys(target);

        for (var i = 0; i < keys.length; i++) {
            if (typeof target[keys[i]] === 'object') {
                if (SVGMap.isChanged(source[keys[i]], target[keys[i]])) {
                    return true;
                }
            } else {
                if (target[keys[i]] !== source[keys[i]]) {
                    return true;
                }
            }
        }

        return false;
    };

    /**
     * Update map objects. Iterate through map objects of specified type and update object attributes.
     *
     * @param {string}    type         Object type (name of SVGMap class attribute).
     * @param {string}    className    Class name used to create instance of a new object.
     * @param {object}    items        Hashmap of map objects.
     * @param {boolean}   incremental  Update method. If set to true, items are added to the existing set of map objects.
     */
    SVGMap.prototype.updateItems = function (type, className, items, incremental) {
        var keys = Object.keys(items);

        if (incremental !== true) {
            Object.keys(this[type]).forEach(function (key) {
                if (keys.indexOf(key) === -1) {
                    this[type][key].remove();
                }
            }, this);
        }

        keys.forEach(function (key) {
            if (typeof this[type][key] !== 'object') {
                this[type][key] = new registry[className](this, {});
            }

            this[type][key].update(items[key]);
        }, this);
    };

    /**
     * Update ordered map objects.
     *
     * @param {string}    type         Object type (name of SVGMap class attribute).
     * @param {string}    idField      Field used to identify objects.
     * @param {string}    className    Class name used to create instance of a new object.
     * @param {object}    items        Array of map objects.
     * @param {boolean}   incremental  Update method. If set to true, items are added to the existing set of map objects.
     */
    SVGMap.prototype.updateOrderedItems = function (type, idField, className, items, incremental) {
        if (incremental !== true) {
            Object.keys(this[type]).forEach(function (key) {
                if (items.filter(function (item) {
                    return item[idField] === key;
                }).length === 0) {
                    this[type][key].remove();
                }
            }, this);
        }

        items.forEach(function (item) {
            if (typeof this[type][item[idField]] !== 'object') {
                this[type][item[idField]] = new registry[className](this, {});
            }

            this[type][item[idField]].update(item);
        }, this);
    };

    /**
     * Update map objects based on specified options.
     *
     * @param {object}    options      Map options.
     * @param {boolean}   incremental  Update method. If set to true, items are added to the existing set of map objects.
     */
    SVGMap.prototype.update = function (options, incremental) {
        var images = {},
                rules = [
                    {
                        name: 'elements',
                        field: 'selementid'
                    },
                    {
                        name: 'links',
                        field: 'linkid'
                    }
                ];

        // elements and links are converted into hashmap as order is not important.
        rules.forEach(function (rule) {
            if (typeof options[rule.name] !== 'undefined') {
                options[rule.name] = SVGMap.toHashmap(options[rule.name], rule.field);
            } else {
                options[rule.name] = {};
            }
        });

        // Performs ordering of shapes based on zindex value.
        if (typeof options.shapes === 'undefined') {
            options.shapes = [];
        } else {
            options.shapes = options.shapes.sort(function (a, b) {
                return a.zindex - b.zindex;
            });
        }

        this.options.label_location = options.label_location;

        // Collect the list of images.
        Object.keys(options.elements).forEach(function (key) {
            var element = options.elements[key];
            if (typeof element.icon !== 'undefined') {
                images[element.icon] = this.getImageUrl(element.icon);
            }
        }, this);

        if (options.background) {
            images[options.background] = this.getImageUrl(options.background);
        }

        // Resize the canvas and move marks
        if (typeof options.canvas !== 'undefined' && typeof options.canvas.width !== 'undefined'
                && typeof options.canvas.height !== 'undefined'
                && this.canvas.resize(options.canvas.width, options.canvas.height)) {

            this.options.canvas = options.canvas;

            if (this.container !== null) {
                this.container.style.width = options.canvas.width + 'px';
                this.container.style.height = options.canvas.height + 'px';
            }

            this.timestamp.update({
                x: options.canvas.width - 107,
                y: options.canvas.height - 6
            });

            this.homepage.update({
                x: options.canvas.width,
                y: options.canvas.height - 50,
                transform: 'rotate(270 ' + (options.canvas.width) + ', ' + (options.canvas.height - 50) + ')'
            });
        }

        // Images are preloaded before update.
        this.imageCache.preload(images, function () {
            // Update is performed after preloading all of the images.
            this.updateItems('elements', 'SVGMapElement', options.elements, incremental);
            this.updateOrderedItems('shapes', 'sysmap_shapeid', 'SVGMapShape', options.shapes, incremental);
            this.updateItems('links', 'SVGMapLink', options.links, incremental);
            this.updateBackground(options.background, incremental);

            this.options = SVGElement.mergeAttributes(this.options, options);
        }, this);

        // Timestamp (date on map) is updated.
        if (typeof options.timestamp !== 'undefined') {
            this.timestamp.element.textContent = options.timestamp;
        }

        // Homepage is updated.
        if (typeof options.homepage !== 'undefined') {
            this.homepage.element.textContent = options.homepage;
        }
    };

    /**
     * Invalidate items based on type.
     *
     * @param {string}    type      Object type (name of SVGMap class attribute).
     */
    SVGMap.prototype.invalidate = function (type) {
        Object.keys(this[type]).forEach(function (key) {
            this[type][key].options = {};
            this[type][key].element.invalidate();
        }, this);
    };

    /**
     * Render map within container.
     *
     * @param {mixed}    container      DOM element or jQuery selector.
     */
    SVGMap.prototype.render = function (container) {
        if (typeof container === 'string') {
            container = jQuery(container)[0];
        }
        this.canvas.render(container);
        this.container = container;
    };

    /*
     * SVGMapElement class. Implements rendering of map elements (selements).
     *
     * @param {object}    map       Parent map.
     * @param {object}    options   Element attributes (match field names in data source).
     */
    function SVGMapElement(map, options) {
        this.map = map;
        this.options = options;
        this.highlight = null;
        this.image = null;
        this.label = null;
        this.markers = null;
    }
    registry['SVGMapElement'] = SVGMapElement;

// Predefined label positions.
    SVGMapElement.LABEL_POSITION_NONE = null;
    SVGMapElement.LABEL_POSITION_DEFAULT = -1;
    SVGMapElement.LABEL_POSITION_BOTTOM = 0;
    SVGMapElement.LABEL_POSITION_LEFT = 1;
    SVGMapElement.LABEL_POSITION_RIGHT = 2;
    SVGMapElement.LABEL_POSITION_TOP = 3;

    /**
     * Remove part (item) of an element.
     *
     * @param {string}    item      Item to be removed.
     */
    SVGMapElement.prototype.removeItem = function (item) {
        if (this[item] !== null) {
            this[item].remove();
            this[item] = null;
        }
    };

    /**
     * Remove element.
     */
    SVGMapElement.prototype.remove = function () {
        ['highlight', 'image', 'label', 'markers'].forEach(function (name) {
            this.removeItem(name);
        }, this);

        delete this.map.elements[this.options.selementid];
    };

    /**
     * Update element highlight (shape and markers placed on the background of element).
     */
    SVGMapElement.prototype.updateHighlight = function () {
        var type = null,
                options = null;

        if (this.options.latelyChanged) {
            var radius = Math.floor(this.width / 2) + 12,
                    markers = [];

            if (this.options.label_location !== SVGMapElement.LABEL_POSITION_BOTTOM) {
                markers.push({
                    type: 'path',
                    attributes: {
                        d: 'M11, 2.91 L5.87, 8 L11, 13.09 L8.07, 16 L0, 8 L8.07, 0, L11, 2.91',
                        transform: 'rotate(90 ' + (this.center.x + 8) + ',' + (this.center.y + radius) + ') translate(' +
                                (this.center.x + 8) + ',' + (this.center.y + radius) + ')'
                    }
                });
            }

            if (this.options.label_location !== SVGMapElement.LABEL_POSITION_LEFT) {
                markers.push({
                    type: 'path',
                    attributes: {
                        d: 'M11, 2.91 L5.87, 8 L11, 13.09 L8.07, 16 L0, 8 L8.07, 0, L11, 2.91',
                        transform: 'rotate(180 ' + (this.center.x - radius) + ',' + (this.center.y + 8) + ') translate(' +
                                (this.center.x - radius) + ',' + (this.center.y + 8) + ')'
                    }
                });
            }

            if (this.options.label_location !== SVGMapElement.LABEL_POSITION_RIGHT) {
                markers.push({
                    type: 'path',
                    attributes: {
                        d: 'M11, 2.91 L5.87, 8 L11, 13.09 L8.07, 16 L0, 8 L8.07, 0, L11, 2.91',
                        transform: 'translate(' + (this.center.x + radius) + ',' + (this.center.y - 8) + ')'
                    }
                });
            }

            if (this.options.label_location !== SVGMapElement.LABEL_POSITION_TOP) {
                markers.push({
                    type: 'path',
                    attributes: {
                        d: 'M11, 2.91 L5.87, 8 L11, 13.09 L8.07, 16 L0, 8 L8.07, 0, L11, 2.91',
                        transform: 'rotate(270 ' + (this.center.x - 8) + ',' + (this.center.y - radius) + ') translate(' +
                                (this.center.x - 8) + ',' + (this.center.y - radius) + ')'
                    }
                });
            }

            var element = this.map.layers.highlights.add('g', {
                fill: '#F44336',
                stroke: '#B71C1C'
            }, markers);

            this.removeItem('markers');
            this.markers = element;
        } else {
            this.removeItem('markers');
        }

        if (typeof this.options.highlight === 'object' && this.options.highlight !== null) {
            if (this.options.highlight.st !== null) {
                type = 'rect';
                options = {
                    x: this.x - 2,
                    y: this.y - 2,
                    width: this.width + 4,
                    height: this.height + 4,
                    fill: '#' + this.options.highlight.st,
                    'fill-opacity': 0.5
                };
            }

            if (this.options.highlight.hl !== null) {
                type = 'ellipse';
                options = {
                    cx: this.center.x,
                    cy: this.center.y,
                    rx: Math.floor(this.width / 2) + 10,
                    ry: Math.floor(this.width / 2) + 10,
                    fill: '#' + this.options.highlight.hl
                };

                if (this.options.highlight.ack === true) {
                    options.stroke = '#329632';
                    options['stroke-width'] = '4px';
                } else {
                    options['stroke-width'] = '0';
                }
            }
        }

        if (type !== null) {
            if (this.highlight === null || type !== this.highlight.type) {
                var element = this.map.layers.highlights.add(type, options);
                this.removeItem('highlight');
                this.highlight = element;
            } else {
                this.highlight.update(options);
            }
        } else {
            this.removeItem('highlight');
        }
    };

    /**
     * Update element image. Image should be pre-loaded and placed in cache before calling this method.
     */
    SVGMapElement.prototype.updateImage = function () {
        var image,
                options = {
                    x: this.x,
                    y: this.y,
                    width: this.width,
                    height: this.height
                };

        if (this.options.actions !== null && this.options.actions !== 'null') {
            options['data-menu-popup'] = this.options.actions;
            options['style'] = 'cursor: pointer';
        }

        if (typeof this.options.icon !== 'undefined') {
            var href = this.map.getImageUrl(this.options.icon);
            // 2 - PERM_READ
            if (2 > this.options.permission) {
                href += '&unavailable=1';
            }

            if (this.image === null || this.image.attributes['xlink:href'] !== href) {
                options['xlink:href'] = href;

                var image = this.map.layers.elements.add('image', options);
                this.removeItem('image');
                this.image = image;
            } else {
                this.image.update(options);
            }
        } else {
            this.removeItem('image');
        }
    };

    /**
     * Update element label.
     */
    SVGMapElement.prototype.updateLabel = function () {
        var x = this.center.x,
                y = this.center.y,
                anchor = {
                    horizontal: 'left',
                    vertical: 'top'
                };

        switch (this.options.label_location) {
            case SVGMapElement.LABEL_POSITION_BOTTOM:
                y = this.y + this.height + this.map.canvas.textPadding;
                anchor.horizontal = 'center';
                break;

            case SVGMapElement.LABEL_POSITION_LEFT:
                x = this.x - this.map.canvas.textPadding;
                anchor.horizontal = 'right';
                anchor.vertical = 'middle';
                break;

            case SVGMapElement.LABEL_POSITION_RIGHT:
                x = this.x + this.width + this.map.canvas.textPadding;
                anchor.vertical = 'middle';
                break;

            case SVGMapElement.LABEL_POSITION_TOP:
                y = this.y - this.map.canvas.textPadding;
                anchor.horizontal = 'center';
                anchor.vertical = 'bottom';
                break;
        }

        if (this.options.label !== null) {
            var element = this.map.layers.elements.add('textarea', {
                'x': x,
                'y': y,
                fill: '#' + this.map.options.theme.textcolor,
                'anchor': anchor,
                background: {
                    fill: '#' + this.map.options.theme.backgroundcolor,
                    opacity: 0.5
                }
            }, this.options.label);

            this.removeItem('label');
            this.label = element;
        } else {
            this.removeItem('label');
        }
    };

    /**
     * Update element (highlight, image and label).
     *
     * @param {object}    options      Element attributes.
     */
    SVGMapElement.prototype.update = function (options) {
        var image = this.map.getImage(options.icon);

        if (image === null) {
            throw "Invalid element configuration!";
        }

        // Data type normalization.
        ['x', 'y', 'width', 'height', 'label_location'].forEach(function (name) {
            if (typeof options[name] !== 'undefined') {
                options[name] = parseInt(options[name]);
            }
        });

        // Inherit label location from map options.
        if (options.label_location === SVGMapElement.LABEL_POSITION_DEFAULT) {
            options.label_location = parseInt(this.map.options.label_location);
        }

        if (typeof options.width !== 'undefined' && typeof options.height !== 'undefined') {
            options.x += Math.floor(options.width / 2) - Math.floor(image.naturalWidth / 2);
            options.y += Math.floor(options.height / 2) - Math.floor(image.naturalHeight / 2);
        }

        options.width = image.naturalWidth;
        options.height = image.naturalHeight;

        if (options.label === null) {
            options.label_location = SVGMapElement.LABEL_POSITION_NONE;
        }

        if (SVGMap.isChanged(this.options, options) === false) {
            // No need to update.
            return;
        }

        this.options = options;

        if (this.x !== options.x || this.y !== options.y || this.width !== options.width
                || this.height !== options.height) {
            ['x', 'y', 'width', 'height'].forEach(function (name) {
                this[name] = options[name];
            }, this);

            this.center = {
                x: this.x + Math.floor(this.width / 2),
                y: this.y + Math.floor(this.height / 2)
            };
        }

        this.updateHighlight();
        this.updateImage();
        this.updateLabel();
    };

    /**
     * SVGMapLink class. Implements rendering of map links.
     *
     * @param {object}    map       Parent map.
     * @param {object}    options   Link attributes.
     */
    function SVGMapLink(map, options) {
        this.map = map;
        this.options = options;
        this.element = null;
    }
    registry['SVGMapLink'] = SVGMapLink;

// Predefined set of line styles
    SVGMapLink.LINE_STYLE_DEFAULT = 0;
    SVGMapLink.LINE_STYLE_BOLD = 2;
    SVGMapLink.LINE_STYLE_DOTTED = 3;
    SVGMapLink.LINE_STYLE_DASHED = 4;

    /**
     * Update link.
     *
     * @param {object}    options   Link attributes (match field names in data source).
     */
    SVGMapLink.prototype.update = function (options) {
        // Data type normalization.
        options.drawtype = parseInt(options.drawtype);
        options.elements = [this.map.elements[options.selementid1], this.map.elements[options.selementid2]];

        if (typeof options.elements[0] === 'undefined' || typeof options.elements[1] === 'undefined') {
            var remove = true;

            if (options.elements[0] === options.elements[1]) {
                // Check if link is from hostgroup to hostgroup.
                options.elements = [
                    this.map.shapes['e-' + options.selementid1],
                    this.map.shapes['e-' + options.selementid2]
                ];

                remove = (typeof options.elements[0] === 'undefined' || typeof options.elements[1] === 'undefined');
            }

            if (remove) {
                // Invalid link configuration.
                this.remove();

                return;
            }
        }

        options.elements[0] = options.elements[0].center;
        options.elements[1] = options.elements[1].center;
        options.center = {
            x: options.elements[0].x + Math.floor((options.elements[1].x - options.elements[0].x) / 2),
            y: options.elements[0].y + Math.floor((options.elements[1].y - options.elements[0].y) / 2)
        };

        if (SVGMap.isChanged(this.options, options) === false) {
            // No need to update.
            return;
        }

        this.options = options;
        this.remove();

        var attributes = {
            stroke: '#' + options.color,
            'stroke-width': 1,
            fill: '#' + this.map.options.theme.backgroundcolor
        };

        switch (options.drawtype) {
            case SVGMapLink.LINE_STYLE_BOLD:
                attributes['stroke-width'] = 2;
                break;

            case SVGMapLink.LINE_STYLE_DOTTED:
                attributes['stroke-dasharray'] = '1,2';
                break;

            case SVGMapLink.LINE_STYLE_DASHED:
                attributes['stroke-dasharray'] = '4,4';
                break;
        }

        this.element = this.map.layers.links.add('g', attributes, [
            {
                type: 'line',
                attributes: {
                    x1: options.elements[0].x,
                    y1: options.elements[0].y,
                    x2: options.elements[1].x,
                    y2: options.elements[1].y
                }
            }
        ]);

        this.element.add('textarea', {
            x: options.center.x,
            y: options.center.y,
            fill: '#' + this.map.options.theme.textcolor,
            'font-size': '10px',
            'stroke-width': 0,
            anchor: {
                horizontal: 'center',
                vertical: 'middle'
            },
            background: {
            }
        }, options.label
                );
    };

    /**
     * Remove link.
     */
    SVGMapLink.prototype.remove = function () {
        if (this.element !== null) {
            this.element.remove();
            this.element = null;
        }
    };

    /**
     * SVGMapShape class. Implements rendering of map shapes.
     *
     * @param {object}    map       Parent map.
     * @param {object}    options   Shape attributes.
     */
    function SVGMapShape(map, options) {
        this.map = map;
        this.options = options;
        this.element = null;
    }
    registry['SVGMapShape'] = SVGMapShape;

// Predefined set of map shape types.
    SVGMapShape.TYPE_RECTANGLE = 0;
    SVGMapShape.TYPE_ELLIPSE = 1;
    SVGMapShape.TYPE_LINE = 2;

// Predefined label horizontal alignments.
    SVGMapShape.LABEL_HALIGN_CENTER = 0;
    SVGMapShape.LABEL_HALIGN_LEFT = 1;
    SVGMapShape.LABEL_HALIGN_RIGHT = 2;

// Predefined label vertical alignments.
    SVGMapShape.LABEL_VALIGN_MIDDLE = 0;
    SVGMapShape.LABEL_VALIGN_TOP = 1;
    SVGMapShape.LABEL_VALIGN_BOTTOM = 2;

    /**
     * Update shape.
     *
     * @param {object}    options        Shape attributes (match field names in data source).
     */
    SVGMapShape.prototype.update = function (options) {
        if (SVGMap.isChanged(this.options, options) === false) {
            // No need to update.
            return;
        }

        this.options = options;

        ['x', 'y', 'width', 'height'].forEach(function (name) {
            this[name] = parseInt(options[name]);
        }, this);

        this.rx = Math.floor(this.width / 2);
        this.ry = Math.floor(this.height / 2);

        this.center = {
            x: this.x + this.rx,
            y: this.y + this.ry
        };

        var type,
                element,
                clip = {},
                attributes = {},
                mapping = [
                    {
                        key: 'background_color',
                        value: 'fill'
                    },
                    {
                        key: 'border_color',
                        value: 'stroke'
                    }
                ];

        mapping.forEach(function (map) {
            if (typeof options[map.key] !== 'undefined' && options[map.key].trim() !== '') {
                attributes[map.value] = '#' + options[map.key];
            } else {
                attributes[map.value] = 'none';
            }
        }, this);

        if (typeof options['border_width'] !== 'undefined') {
            attributes['stroke-width'] = parseInt(options['border_width']);
        }

        if (typeof options['border_type'] !== 'undefined') {
            var border_type = SVGMap.BORDER_TYPES[parseInt(options['border_type'])];

            if (border_type !== '' && border_type !== 'none' && attributes['stroke-width'] > 1) {
                var parts = border_type.split(',').map(function (value) {
                    return parseInt(value);
                });

                // Make dots round.
                if (parts[0] === 1 && attributes['stroke-width'] > 2) {
                    attributes['stroke-linecap'] = 'round';
                }

                border_type = parts.map(function (part) {
                    if (part === 1 && attributes['stroke-width'] > 2) {
                        return 1;
                    }

                    return part * attributes['stroke-width'];
                }).join(',');
            }

            if (border_type !== '') {
                attributes['stroke-dasharray'] = border_type;
            } else {
                attributes['stroke-width'] = 0;
            }
        }

        switch (parseInt(options.type)) {
            case SVGMapShape.TYPE_RECTANGLE:
                type = 'rect';
                attributes = SVGElement.mergeAttributes(attributes, {
                    x: this.x,
                    y: this.y,
                    width: this.width,
                    height: this.height
                });

                clip = {
                    x: this.x,
                    y: this.y,
                    width: this.width,
                    height: this.height
                };
                break;

            case SVGMapShape.TYPE_ELLIPSE:
                type = 'ellipse';
                attributes = SVGElement.mergeAttributes(attributes, {
                    cx: this.center.x,
                    cy: this.center.y,
                    rx: this.rx,
                    ry: this.ry
                });

                clip = {
                    cx: this.center.x,
                    cy: this.center.y,
                    rx: this.rx,
                    ry: this.ry
                };
                break;

            case SVGMapShape.TYPE_LINE:
                type = 'line';

                delete attributes['fill'];
                delete options['text'];
                attributes = SVGElement.mergeAttributes(attributes, {
                    x1: this.x,
                    y1: this.y,
                    x2: this.width,
                    y2: this.height
                });
                break;

            default:
                throw "Invalid shape configuration!";
        }

        if (typeof options.text === 'undefined' || options.text.trim() === '') {
            element = this.map.layers.shapes.add(type, attributes);
        } else {
            element = this.map.layers.shapes.add('g', null, [{
                    'type': type,
                    'attributes': attributes
                }]);

            var x = this.center.x,
                    y = this.center.y,
                    anchor = {
                        horizontal: 'center',
                        vertical: 'middle'
                    };

            switch (parseInt(options['text_halign'])) {
                case SVGMapShape.LABEL_HALIGN_LEFT:
                    x = this.x + this.map.canvas.textPadding;
                    anchor.horizontal = 'left';
                    break;

                case SVGMapShape.LABEL_HALIGN_RIGHT:
                    x = this.x + this.width - this.map.canvas.textPadding;
                    anchor.horizontal = 'right';
                    break;
            }

            switch (parseInt(options['text_valign'])) {
                case SVGMapShape.LABEL_VALIGN_TOP:
                    y = this.y + this.map.canvas.textPadding;
                    anchor.vertical = 'top';
                    break;

                case SVGMapShape.LABEL_VALIGN_BOTTOM:
                    y = this.y + this.height - this.map.canvas.textPadding;
                    anchor.vertical = 'bottom';
                    break;
            }

            element.add('textarea', {
                'x': x,
                'y': y,
                fill: '#' + options['font_color'],
                'font-family': SVGMap.FONTS[parseInt(options.font)],
                'font-size': parseInt(options['font_size']) + 'px',
                'anchor': anchor,
                clip: {
                    'type': type,
                    'attributes': clip
                },
                'parse-links': true
            }, options.text);
        }

        this.replace(element);
    };

    /**
     * Replace shape.
     *
     * @see SVGElement.prototype.replace
     *
     * @param {object}    element   New shape element.
     */
    SVGMapShape.prototype.replace = function (element) {
        if (this.element !== null) {
            this.element.replace(element);
        } else {
            this.element = element;
        }
    };

    /**
     * Remove shape.
     */
    SVGMapShape.prototype.remove = function () {
        if (this.element !== null) {
            delete this.map.shapes[this.options.sysmap_shapeid];

            this.element.remove();
            this.element = null;
        }
    };

    /*
     define('com.mesilat.zabbix:svg-map',[],function(){
     return SVGMap;
     });
     */
    $(function () {
        function generateUUID() {
            var s4 = function() {
                return Math.floor((1 + Math.random()) * 0x10000)
                        .toString(16)
                        .substring(1);
            };
            return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
                    s4() + '-' + s4() + s4() + s4();
        }
        function addContextMenu($div){
            $div.find('g.map-elements image').each(function(){
                var $image = $(this);
                if (typeof $image.attr('data-menu-popup') !== 'undefined'){
                    var itemData = JSON.parse($image.attr('data-menu-popup')),
                        id = generateUUID();

                    if (!!(itemData.urls)){
                        AJS.InlineDialog($image, id,
                            function(content, trigger, showPopup) {
                                var html = Mesilat.Zabbix.Templates.zabbixMapContext({
                                    urls: itemData.urls
                                });
                                content.css({
                                    padding: 0,
                                    'padding-top':'10px',
                                    'padding-bottom':'10px'
                                }).html(html);
                                showPopup();
                                return false;
                            }
                        );                
                    } else {
                        $image.css({
                            cursor: 'default'
                        });
                    }
                }
            });
        }

        
        $('div.zabbix-svg-map').each(function () {
            var $div = $(this),
                $pre = $div.find('pre');

            if (typeof $pre.spin === 'function'){
                $pre.find('span').spin();
            }

            $.ajax({
                url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/image/map-svg',
                type: 'GET',
                data: {
                    server:   $div.data('server'),
                    map:      $div.data('map'),
                    severity: $div.data('severity')
                },
                dataType: 'json',
                context: this
            }).done(function (data) {
                //console.log('zabbix-svg-map', data);
                var id = (++counter);
                $(this).empty().attr('id', 'map_' + id).attr('style', 'overflow: hidden;');
                var w = {
                    'id': 'mapimg',
                    'interval': '30',
                    'isFlickerfree': true,
                    'mode': 0,
                    'timestamp': (new Date()).getMilliseconds(),
                    'resourcetype': 2,
                    'screenid': null,
                    'groupid': null,
                    'hostid': 0,
                    'pageFile': null,
                    'profileIdx': '',
                    'profileIdx2': null,
                    'screenitemid': '' + id,
                    'data': data
                };

                w.data.id = id;
                w.data.theme = theme;
                w.data.refresh = 'do not refresh';
                w.data.homepage = 'http:\/\/www.zabbix.com';
                w.data.container = '#map_' + id;

                window.flickerfreeScreen.add(w, $div.data('server'));

                setTimeout(function(){
                    addContextMenu($div);
                }, 100);
            }).fail(function (jqxhr) {
                //$(this).text(jqxhr.responseText);
                $pre.empty().append(AJS.I18n.getText('com.mesilat.zabbix-plugin.zabbix-map.label') + ': ' + jqxhr.responseText);
            });
        });
    });
})(AJS.$ || $);