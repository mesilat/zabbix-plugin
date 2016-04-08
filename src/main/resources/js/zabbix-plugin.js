(function ($) {
    $(document).ready(function() {
        if (AJS.$("#config").length) {
            readConfig();
        }
    });
})(AJS.$ || jQuery);

function readConfig() {
    var url = AJS.contextPath() + "/rest/zabbix-plugin/1.0/config";

    AJS.$.ajax({
        url: url,
        dataType: "json"
    }).done(function(config) {
        AJS.$("#url").val(config.url);
        AJS.$("#username").val(config.username);
        AJS.$("#password").val(config.password);
    });

    AJS.$("#config").submit(function(e) {
        e.preventDefault();
        updateConfig();
    });
}

function updateConfig() {
    var url = AJS.contextPath() + "/rest/zabbix-plugin/1.0/config";

    AJS.$.ajax({
        url: url,
        type: "PUT",
        contentType: "application/json",
        data: '{ "url": "' + AJS.$("#url").attr("value")
                + '", "username": "' + AJS.$("#username").attr("value")
                + '", "password": "' + AJS.$("#password").attr("value")
                + '" }',
        processData: false,
        dataType: "json",
        success: function(data) {
            if (data.status === "SUCCESS") {
                $("#config-result").html("<p>" + data.message + "</p>")
                    .css( "display", "block")
                    .removeClass()
                    .addClass("aui-message aui-message-success fadeout");
            } else if (data.status === "FAILURE") {
                $("#config-result").html("<p>" + data.message + "</p>")
                    .css( "display", "block")
                    .removeClass()
                    .addClass("aui-message aui-message-warning fadeout");
            }
            setTimeout(fadeOut, 2000);
        },
        error: function(jqXHR) {
            $("#config-result")
                .html("<p>" + jqXHR.responseText + "</p>")
                .css( "display", "block")
                .removeClass()
                .addClass("aui-message aui-message-error fadeout");
            setTimeout(fadeOut, 2000);
        }
    });
}

function fadeOut() {
    AJS.$(".fadeout").each(function(){
        $(this).removeClass("fadeout");
        $(this).hide(500);
    });
}
