/** THIS IS TO SUPPORT LEGACY MACROS */

define('zabbix-plugin/util', [], function() {
    return {
        entityMap: {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': '&quot;',
            "'": '&#39;',
            "/": '&#x2F;'
        },
        formatMessage: function() {
            var args = Array.prototype.slice.call(arguments);
            var params = args.slice(1);
            return args[0].replace(/\{(\d+)\}/g, function(m,n){
                    return params[n] ? params[n] : m;
            });
        },
        blockError: function(obj,title,message) {
            $(obj).empty().append(Mesilat.Zabbix.Templates.blockError({
                title: title,
                text: message
            }));
        },
        blockWarning: function(obj,title,message) {
            $(obj).empty().append(Mesilat.Zabbix.Templates.blockWarning({
                title: title,
                text: message
            }));
        },
        blockSuccess: function(obj,title,message) {
            $(obj).empty().append(Mesilat.Zabbix.Templates.blockSuccess({
                title: title,
                text: message
            }));
        },
        escape: function(text) {
            var o = this.entityMap;
            return String(text).replace(/[&<>"'\/]/g, function (s) {
                return o[s];
            });
        }
    };
});

define('zabbix-plugin/macro-preview-extender', [], function() {
    var o = { debug: false };
    o.init = function() {
        if (this.initialized === true) {
            this.zabbixForm.hide();
            this.zabbixForm.empty();
            this.macroBrowserPreview.show();
            this.macroBrowserPreviewHeader.find('ul').show();
            this.macroBrowserPreviewHeader.find('span').text(this.macroBrowserPreviewHeaderOriginalText);
            this.headerText = "Zabbix Form";
            this.currentView = 0;
            return true;
        } else {
            if ($('#macro-browser-preview').length) {
                this.macroBrowserPreview = $('#macro-browser-preview');
                this.macroBrowserPreviewHeader = this.macroBrowserPreview.parent().find('div.macro-preview-header');
                this.macroBrowserPreviewHeaderOriginalText = this.macroBrowserPreviewHeader.find('span').text();
                this.macroBrowserPreview.parent().append('<div id="zabbix-form" style="display: none;"></div>');
                this.zabbixForm = $('#zabbix-form');
                this.headerText = "Zabbix Form";
                this.currentView = 0;
                this.initialized = true;
                return true;
            } else {
                return false;
            }
        }
    };
    o.switchView = function() {
        if (!this.initialized) {
            console.log('zabbix-plugin/macro-preview-extender: not initialized');
            return;
        }
        if (this.currentView) {
            // Switch to original view
            this.zabbixForm.hide();
            this.macroBrowserPreview.show();
            this.macroBrowserPreviewHeader.find('ul').show();
            this.macroBrowserPreviewHeader.find('span').text(this.macroBrowserPreviewHeaderOriginalText);
            this.currentView = 0;
        } else {
            // Switch to Zabbix form view
            this.zabbixForm.show();
            this.macroBrowserPreview.hide();
            this.macroBrowserPreviewHeader.find('ul').hide();
            this.macroBrowserPreviewHeader.find('span').text(this.headerText);
            this.currentView = 1;
        }
    };
    o.installHook = function(hookedParamDiv) {
        this.hookedParamDiv = hookedParamDiv;
        if (hookedParamDiv.find("a.zabbix-hook").length) {
            return;
        }
        var macroParamDesc = hookedParamDiv.find('div.macro-param-desc');
        var description = macroParamDesc.text();
        macroParamDesc.empty().append('<a href="#" class="button-panel-link zabbix-hook"></a>');
        var a = macroParamDesc.find('a')
            .text(description)
            .mouseover(function(){
                // ColorSplitButton.js installs its own handler on "click" event, which spoils everything
                a.off('click').click(function(e){
                    e.preventDefault();
                    o.switchView();
                });
            });
        $('div#macro-browser-dialog button.ok').on('click', function(){
            o.close();
        });
    };
    o.close = function() {
        if (this.currentView) {
            // Switch to original view
            this.zabbixForm.hide();
            this.macroBrowserPreview.show();
            this.macroBrowserPreviewHeader.find('ul').show();
            this.macroBrowserPreviewHeader.find('span').text(this.macroBrowserPreviewHeaderOriginalText);
            this.currentView = 0;
        }
    };
    return o;
});

define('zabbix-plugin/graph-select-form', ['jquery','zabbix-plugin/util'], function($,util) {
    return {
        init: function(){
            this.ext = require('zabbix-plugin/macro-preview-extender');
            if (!this.ext.init()) {
                console.log('zabbix-plugin/graph-select-form: failed to init macro-preview-extender');
                return;
            }
            if ($('#macro-param-div-graphid').length) {
                this.ext.installHook($('#macro-param-div-graphid'));
            } else {
                console.log('zabbix-plugin/graph-select-form: failed to install graph select hook');
                return;
            }
            this.ext.headerText = AJS.I18n.getText("com.mesilat.zabbix-plugin.graph.title");
            this.installGraphSelectForm(this.ext.zabbixForm);
        },
        installGraphSelectForm: function(zabbixForm) {
            var o = this;
            var $form = Mesilat.Zabbix.Templates.graphSelectForm({});
            zabbixForm.append($form);

            AJS.$('#zabbix-host').auiSelect2({
                ajax: {
                    url:      AJS.contextPath() + '/rest/zabbix-plugin/1.0/host',
                    type:     'GET',
                    dataType: 'json',
                    delay:    250,
                    data: function (searchTerm) {
                            return {
                                "q": searchTerm
                            };
                        },
                    results: function (data) {
                            return data;
                        }
                },
                minimumInputLength: 3,
                escapeMarkup: function (markup) {
                    return markup;
                }
            })
            .on('change', function(e) {
                zabbixForm.find('ul').empty();
                AJS.$.ajax({
                    url:         AJS.contextPath() + '/rest/zabbix-plugin/1.0/graph',
                    type:        'GET',
                    data:        {hostid: e.val},
                    dataType:    'json'
                }).done(function(data) {
                    data.results.forEach(function(graph){
                        zabbixForm.find('ul').append('<li><a href="#"></a></li>')
                            .find('a').last()
                            .text(graph.text)
                            .click(function(e){
                                e.preventDefault();
                                o.selectedGraphId(graph.id);
                            });
                    });
                }).fail(function(jqXHR){
                    zabbixForm.find('ul').append('<li></li>').find('li').last().text(jqXHR.responseText);
                });
            });

            zabbixForm.find('a.cancel').click(function(e){
                e.preventDefault();
                o.ext.switchView();
            });
        },
        selectedGraphId: function(graphId) {
            $('#macro-param-graphid').val(graphId).trigger('change');
            this.ext.switchView();
        },
        onClose: function() {
            this.ext.close();
        }
    };
});

define('zabbix-plugin/item-select-form', [], function() {
    return {
        init: function() {
            this.ext = require('zabbix-plugin/macro-preview-extender');
            if (!this.ext.init()) {
                console.log('zabbix-plugin/item-select-form: failed to init macro-preview-extender');
                return;
            }
            if ($('#macro-param-div-itemid').length) {
                this.ext.installHook($('#macro-param-div-itemid'));
            } else {
                console.log('zabbix-plugin/item-select-form: failed to install item select hook');
                return;
            }
            this.ext.headerText = AJS.I18n.getText("com.mesilat.zabbix-plugin.item.title");
            this.installItemSelectForm(this.ext.zabbixForm);
            if (AJS.$('#macro-param-format').length) {
                AJS.$.ajax({
                    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/format',
                    dataType: 'json',
                    context: AJS.$('#macro-param-format')
                }).done(function(formats) {
                    var formatNames = [];
                    formats.forEach(function(format) {
                        formatNames.push(format.name);
                    });
                    formatNames.sort();
                    AJS.$(this).autocomplete({ source: formatNames });
                });
            }
        },
        installItemSelectForm: function(zabbixForm) {
            var o = this;
            var $form = Mesilat.Zabbix.Templates.itemSelectForm({});
            zabbixForm.append($form);

            AJS.$('#zabbix-host').auiSelect2({
                ajax: {
                    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/host',
                    type: 'GET',
                    dataType: 'json',
                    delay: 250,
                    data: function (searchTerm) {
                            return {
                                "q": searchTerm
                            };
                        },
                    results: function (data) {
                            return data;
                        }
                },
                minimumInputLength: 3,
                escapeMarkup: function (markup) {
                    return markup;
                }
            })
            .on('change', function() {
                $('#zabbix-item').val('');
            });

            $('#zabbix-item').auiSelect2({
                ajax: {
                    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/items',
                    type: 'GET',
                    dataType: 'json',
                    delay: 250,
                    data: function (searchTerm) {
                            return {
                                "hostid": $('#zabbix-host').val(),
                                "q": searchTerm
                            };
                        },
                    results: function (data) {
                            return data;
                        }
                },
                minimumInputLength: 3,
                escapeMarkup: function (markup) {
                    return markup;
                }
            })
            .on('change', function(e) {
                o.selectedItemId($('#zabbix-item').val());
                o.ext.switchView();
            });

            zabbixForm.find('a.cancel').click(function(e){
                e.preventDefault();
                o.ext.switchView();
            });
        },
        selectedItemId: function(itemId) {
            $('#macro-param-itemid').val(itemId).trigger('change');
            this.ext.switchView();
        },
        onClose: function() {
            this.ext.close();
        }
    };
});

define('zabbix-plugin/host-select-form', [], function() {
    return {
        init: function() {
            this.ext = require('zabbix-plugin/macro-preview-extender');
            if (!this.ext.init()) {
                console.log('zabbix-plugin/host-select-form: failed to init macro-preview-extender');
                return;
            }
            if ($('#macro-param-div-hostid').length) {
                this.ext.installHook($('#macro-param-div-hostid'));
            } else {
                console.log('zabbix-plugin/host-select-form: failed to install host select hook');
                return;
            }
            this.ext.headerText = AJS.I18n.getText("com.mesilat.zabbix-plugin.host.title");
            this.installItemSelectForm(this.ext.zabbixForm);
        },
        installItemSelectForm: function(zabbixForm) {
            var o = this;
            var $form = Mesilat.Zabbix.Templates.hostSelectForm({});
            zabbixForm.append($form);

            AJS.$('#zabbix-host').auiSelect2({
                ajax: {
                    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/host',
                    type: 'GET',
                    dataType: 'json',
                    delay: 250,
                    data: function (searchTerm) {
                            return {
                                "q": searchTerm
                            };
                        },
                    results: function (data) {
                            return data;
                        }
                },
                minimumInputLength: 3,
                escapeMarkup: function (markup) {
                    return markup;
                }
            })
            .on('change', function() {
                o.selectedHostId($('#zabbix-host').val());
                o.ext.switchView();
            });

            zabbixForm.find('a.cancel').click(function(e){
                e.preventDefault();
                o.ext.switchView();
            });
        },
        selectedHostId: function(hostId) {
            $('#macro-param-hostid').val(hostId).trigger('change');
            this.ext.switchView();
        },
        onClose: function() {
            this.ext.close();
        }
    };
});

define('zabbix-plugin/config', ['zabbix-plugin/util'], function(util){
    return {
        readConfig: function(){
            var o = this;
            AJS.$.ajax({
                url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/config',
                dataType: 'json'
            }).done(function(config) {
                AJS.$('#url').val(config.url);
                AJS.$('#username').val(config.username);
                AJS.$('#password').val(config.password);
            });

            AJS.$('#config').submit(function(e) {
                e.preventDefault();
                o.updateConfig();
            });

            AJS.$.ajax({
                url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/format',
                dataType: 'json'
            }).done(function(formats){
                $('table#list-of-named-formats tbody').html('');
                formats.forEach(function(fmt){
                    $('table#list-of-named-formats tbody')
                        .append('<tr><td class="confluenceTd">'
                            + util.escape(fmt.name)
                            + '</td><td class="confluenceTd">'
                            + util.escape(fmt.format)
                            + '</td></tr>');
                });
            });

            AJS.$('#named-formats').submit(function(e){
                e.preventDefault();
                AJS.$.ajax({
                    url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/format',
                    type: 'PUT',
                    contentType: "application/json",
                    data: JSON.stringify({name: $('input#name').val(), format: $('input#format').val()}),
                    processData: false,
                    dataType: 'json'
                }).done(function(formats){
                    console.log(formats);
                    $('table#list-of-named-formats tbody').html('');
                    formats.forEach(function(fmt){
                        $('table#list-of-named-formats tbody')
                            .append('<tr><td class="confluenceTd">'
                                + util.escape(fmt.name)
                                + '</td><td class="confluenceTd">'
                                + util.escape(fmt.format)
                                + '</td></tr>');
                    });
                });
            });
        },
        updateConfig: function() {
            var o = this;
            AJS.$.ajax({
                url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/config',
                type: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify({
                    url: AJS.$('#url').attr('value'),
                    username: AJS.$('#username').attr('value'),
                    password: AJS.$('#password').attr('value')
                }),
                processData: false,
                dataType: 'json'
            }).done(function(data){
                $('#config-result').html('<p>' + util.escape(data) + '</p>')
                    .css('display', 'block')
                    .removeClass()
                    .addClass('aui-message aui-message-success fadeout');
                setTimeout(o.fadeOut, 2000);
            }).fail(function(jqXHR){
                $('#config-result')
                    .html('<p>' + util.escape(jqXHR.responseText) + '</p>')
                    .css( 'display', 'block')
                    .removeClass()
                    .addClass('aui-message aui-message-error fadeout');
                setTimeout(o.fadeOut, 2000);
            });
        },
        fadeOut: function() {
            AJS.$('.fadeout').each(function(){
                $(this).removeClass('fadeout');
                $(this).hide(500);
            });
        }
    };
});

AJS.MacroBrowser.setMacroJsOverride('zabbix-graph-old', {
    beforeParamsSet: function(selectedParams, macroSelected) {
        var o = require('zabbix-plugin/graph-select-form');
        o.selectedParams = selectedParams;
        o.macroSelected = macroSelected;
        o.init();
        if (typeof AJS.MacroBrowser.settings.onCancel === 'function') {
            var old = AJS.MacroBrowser.settings.onCancel;
            AJS.MacroBrowser.settings.onCancel = function() {
                old();
                o.onClose();
            };
        } else {
            AJS.MacroBrowser.settings.onCancel = function() {
                o.onClose();
            };
        }

        return selectedParams;
    }
});

AJS.MacroBrowser.setMacroJsOverride('zabbix-item-old', {
    beforeParamsSet: function(selectedParams, macroSelected) {
        console.log('Zabbix', AJS.MacroBrowser.settings);
        var o = require('zabbix-plugin/item-select-form');
        o.selectedParams = selectedParams;
        o.macroSelected = macroSelected;
        o.init();
        if (typeof AJS.MacroBrowser.settings.onCancel === 'function') {
            var old = AJS.MacroBrowser.settings.onCancel;
            AJS.MacroBrowser.settings.onCancel = function() {
                old();
                o.onClose();
            };
        } else {
            AJS.MacroBrowser.settings.onCancel = function() {
                o.onClose();
            };
        }

        return selectedParams;
    }
});

AJS.MacroBrowser.setMacroJsOverride('zabbix-triggers-old', {
    beforeParamsSet: function(selectedParams, macroSelected) {
        console.log('Zabbix', AJS.MacroBrowser.settings);
        var o = require('zabbix-plugin/host-select-form');
        o.selectedParams = selectedParams;
        o.macroSelected = macroSelected;
        o.init();
        if (typeof AJS.MacroBrowser.settings.onCancel === 'function') {
            var old = AJS.MacroBrowser.settings.onCancel;
            AJS.MacroBrowser.settings.onCancel = function() {
                old();
                o.onClose();
            };
        } else {
            AJS.MacroBrowser.settings.onCancel = function() {
                o.onClose();
            };
        }

        return selectedParams;
    }
});

(function ($) {
    $(document).ready(function() {
        var util = require('zabbix-plugin/util');

        var items = []
        var itemids = [];
        var formats = [];
        var hostIds = [];

        $('.zabbix-item-old').each(function() {
            items.push($(this));
            itemids.push($(this).attr('item-id'));
            formats.push($(this).find('span.item-format').text());
            $(this).spin();
        });

        $('.zabbix-triggers-old').each(function() {
            if (hostIds.indexOf($(this).attr('host-id')) === -1) {
                hostIds.push($(this).attr('host-id'));
            }
            $(this).spin();
        });

        if (items.length > 0) {
            $.ajax({
                url: AJS.contextPath() + '/rest/zabbix-plugin/1.0/items',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify([itemids,formats]),
                processData: false,
                dataType: 'json',
                success: function(data) {
                    if (data.results) {
                        for (var i = 0; i < items.length; i ++) {
                            items[i].spinStop();
                            if (data.results.length > i) {
                                items[i].html(data.results[i]);
                            }
                        }
                    } else {
                        console.log('zabbix-plugin: invalid data received from REST service');
                        $('.zabbix-item').each(function() {
                            $(this).spinStop();
                            $(this).append('<a href="#">' + AJS.I18n.getText("com.mesilat.zabbix-plugin.common.error") + '</a>');
                            AJS.$(
                                $(this).find('a').attr('title', AJS.I18n.getText("com.mesilat.zabbix-plugin.error.unexpected"))
                            ).tooltip();
                        });
                    }
                },
                error: function(jqXHR) {
                    console.log('zabbix-plugin: ' + jqXHR.responseText);
                    $('.zabbix-item').each(function() {
                        $(this).spinStop();
                        $(this).append('<a href="#">' + AJS.I18n.getText("com.mesilat.zabbix-plugin.common.error") + '</a>');
                        AJS.$(
                            $(this).find('a').attr('title', jqXHR.responseText)
                        ).tooltip();
                    });
                }
            });
        }

        if (hostIds.length > 0) {
            $.ajax({
                url:         AJS.contextPath() + '/rest/zabbix-plugin/1.0/triggers',
                type:        'POST',
                contentType: 'application/json',
                data:        JSON.stringify(hostIds),
                processData: false,
                dataType:    'json',
                success: function(data) {
                    $('.zabbix-triggers').each(function() {
                        $(this).spinStop();
                        var triggers = data['host_' + $(this).attr('host-id')];
                        if (typeof triggers === 'undefined') {
                            util.blockSuccess(this,
                                AJS.I18n.getText('com.mesilat.zabbix-plugin.error'),
                                AJS.I18n.getText('com.mesilat.zabbix-plugin.error.unexpected')
                            );
                        } else if (triggers.length === 0) {
                            util.blockSuccess(this,
                                AJS.I18n.getText('com.mesilat.zabbix-plugin.allgood'),
                                AJS.I18n.getText('com.mesilat.zabbix-plugin.no-triggers')
                            );
                        } else {
                            var $triggers = Mesilat.Zabbix.Templates.zabbixTriggersSync({
                                triggers: triggers
                            });
                            $(this).append($triggers);

                            if ($(this).hasClass('zabbix-plugin-not-licensed') && triggers.length > 0 && triggers[0].totalTriggers > 1) {
                                $(this).append('<div class="wiki-content"></div>');
                                util.blockWarning($(this).find('div').last(),
                                    AJS.I18n.getText("com.mesilat.zabbix-plugin.error.license"),
                                    AJS.I18n.getText("com.mesilat.zabbix-plugin.warning.no-license")// util.formatMessage(AJS.I18n.getText("com.mesilat.zabbix-plugin.warning.no-license"), triggers[0].totalTriggers)
                                );
                            }
                        }
                    });
                },
                error: function(jqXHR) {
                    $('.zabbix-triggers').each(function() {
                        $(this).spinStop();
                        util.blockError(this,
                            AJS.I18n.getText("com.mesilat.zabbix-plugin.common.error"),
                            jqXHR.responseText
                        );
                    });
                }
            });
        }

        if ($('#config').length) {
            require('zabbix-plugin/config').readConfig();
        }

        $('#delete-zabbix-settings').on('click', function(e){
            e.preventDefault();
            AJS.$.ajax({
                url:         AJS.contextPath() + '/rest/zabbix-plugin/1.0/config',
                type:        'DELETE',
                dataType:    'text'
            }).done(function(text){
                alert(text);
            }).fail(function(jqXHR){
                alert(jqXHR.responseText);
            });
        })
    });
})(AJS.$ || jQuery);
