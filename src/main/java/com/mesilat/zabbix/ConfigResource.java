package com.mesilat.zabbix;

import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/config")
public class ConfigResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    private final UserManager userManager;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final TransactionTemplate transactionTemplate;
    private final I18nResolver resolver;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpServletRequest request) {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isSystemAdmin(userKey)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        LOGGER.debug("Return Zabbix Plugin settings");
        return Response.ok(transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction() {
                PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
                return new Config(settings);
            }
        })).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(final Config config, @Context HttpServletRequest request) {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isSystemAdmin(userKey)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (!config.isPasswordObfuscated()) {
            try {
                config.obfuscatePassword();
            } catch(Exception ex) {
                LOGGER.warn("Please consider using \"strong\" cryptography to obfuscate passwords");
            }
        }
        LOGGER.debug("Save Zabbix Plugin settings");
        return Response.ok(transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction() {
                PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
                config.save(settings);
                return new Result(Result.STATUS_SUCCESS, resolver.getText("zabbix-plugin.config.save.success"));
            }})
        ).build();
    }

    public ConfigResource(UserManager userManager, PluginSettingsFactory pluginSettingsFactory,
            TransactionTemplate transactionTemplate, I18nResolver resolver) {
        this.userManager = userManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
        this.resolver = resolver;
    }
}
