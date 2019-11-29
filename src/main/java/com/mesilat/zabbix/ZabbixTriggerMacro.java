package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixTrigger;
import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.renderer.template.TemplateRenderer;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUserPreferences;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.TokenType;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.mesilat.zabbix.client.ZabbixClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZabbixTriggerMacro extends ZabbixMacroBase implements Macro {
    private static final String ALL_HOSTS = "__ALL__";

    @Override
    public TokenType getTokenType(Map parameters, String body, RenderContext context) {
        return TokenType.INLINE;
    }
    @Override
    public boolean hasBody() {
        return false;
    }
    @Override
    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }
    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }
    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }
    @Override
    public String execute(Map parameters, String body, RenderContext renderContext) {
        return body;
    }
    @Override
    public String execute(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        UserKey userKey = getUserManager().getRemoteUserKey();

        if (params.containsKey("hostid")){
            return executeLegacy(userKey, params, body, conversionContext);
        } else {
            return execute(userKey, params, body, conversionContext);
        }
    }
    public String execute(UserKey userKey, Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        String server;
        String host;
        if (params.get("server") == null || params.get("host") == null){
            ZabbixHostDefault hd = getHostDefault(conversionContext.getEntity().getId());
            if (hd == null){
                throw new MacroExecutionException(getResolver().getText("com.mesilat.zabbix-plugin.error.mandatory-params-not-found"));
            }
            server = hd.getServer();
            host = hd.getHost();
        } else {
            server = params.get("server").toString();
            host = params.get("host").toString();
        }

        if ("preview".equals(conversionContext.getOutputType())) {
            ConfluenceUserPreferences preferences = getPreferences(userKey);

            try {
                ZabbixConnectionDescriptor conn = getConnection(userKey, Integer.parseInt(server));
                ZabbixClient client = getClient(conn);
                List<ZabbixTrigger> triggers;
                if (ALL_HOSTS.equals(host)) {
                    triggers = client.getTriggers(new DateFormatterImpl(getDateFormatter(preferences)));
                } else if ("true".equals(params.get("group"))) {
                    triggers = client.getTriggersForHostGroup(host, new DateFormatterImpl(getDateFormatter(preferences)));
                } else {
                    triggers = client.getTriggersForHost(host, new DateFormatterImpl(getDateFormatter(preferences)));
                }

                if (triggers.isEmpty()) {
                    return renderFromSoy("Mesilat.Zabbix.Templates.zabbixAllGood.soy", new HashMap<>());
                } else {
                    Map<String,Object> map = new HashMap<>();
                    map.put("triggers", triggers);
                    return renderFromSoy("Mesilat.Zabbix.Templates.zabbixTriggersSync.soy", map);
                }
            } catch (ZabbixResourceException | ZabbixClientException ex) {
                throw new MacroExecutionException(ex);
            }            
        } else {
            Map<String,Object> map = new HashMap<>();
            map.put("server", server);
            map.put("host", host);
            if (params.containsKey("group") && "true".equals(params.get("group"))) {
                map.put("group", true);
            }
            map.put("licensed", isLicensed());
            map.put("token", TokenService.createToken(userKey));

            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixTriggers2.soy", map);
        }
    }
    public String executeLegacy(UserKey userKey, Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        if ("preview".equals(conversionContext.getOutputType())) {
            try {
                ConfluenceUserPreferences preferences = getPreferences(userKey);
                ZabbixConnectionDescriptor conn = getDefaultConnection();
                List<ZabbixTrigger> triggers = getClient(conn).getTriggersForHostId(params.get("hostid").toString(), new DateFormatterImpl(getDateFormatter(preferences)));
                if (triggers.isEmpty()) {
                    return renderFromSoy("Mesilat.Zabbix.Templates.zabbixAllGood.soy", new HashMap<>());
                } else {
                    Map<String,Object> map = new HashMap<>();
                    map.put("triggers", triggers);
                    return renderFromSoy("Mesilat.Zabbix.Templates.zabbixTriggersSync.soy", map);
                }
            } catch(ZabbixResourceException | ZabbixClientException ex) {
                throw new MacroExecutionException(ex);
            }
        } else {
            Map<String,Object> map = new HashMap<>();
            map.put("hostId", params.get("hostid"));
            map.put("licensed", isLicensed());
            map.put("token", TokenService.createToken(userKey));
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixTriggers.soy", map);
        }
    }

    public ZabbixTriggerMacro(UserManager userManager, I18nResolver resolver,
            SettingsManager settingsManager, PluginLicenseManager licenseManager,
            UserAccessor userAccessor, FormatSettingsManager formatSettingsManager,
            LocaleManager localeManager, TemplateRenderer renderer,
            PageBuilderService pageBuilderService, ActiveObjects ao) {

        super(userManager, resolver, settingsManager,
                licenseManager, userAccessor, formatSettingsManager, localeManager,
                renderer, pageBuilderService, ao);
    }
}