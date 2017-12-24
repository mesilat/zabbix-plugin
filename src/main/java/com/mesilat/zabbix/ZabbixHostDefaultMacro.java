package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.renderer.template.TemplateRenderer;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.TokenType;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import java.util.HashMap;
import java.util.Map;

public class ZabbixHostDefaultMacro extends ZabbixMacroBase implements Macro {
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
        return OutputType.INLINE;
    }
    @Override
    public String execute(Map parameters, String body, RenderContext renderContext) {
        return body;
    }
    @Override
    public String execute(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        UserKey userKey = getUserManager().getRemoteUserKey();

        if (params.containsKey("server") && params.containsKey("host")){
            return execute(userKey, params, body, conversionContext);
        } else {
            throw new MacroExecutionException(getResolver().getText("com.mesilat.zabbix-plugin.error.mandatory-params-not-found"));
        }
    }
    public String execute(UserKey userKey, Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        Map<String,Object> map = new HashMap<>();
        map.put("server", params.get("server"));
        map.put("host", params.get("host"));
        map.put("licensed", isLicensed());

        return renderFromSoy("Mesilat.Zabbix.Templates.zabbixHostDefault.soy", map);
    }

    public ZabbixHostDefaultMacro(UserManager userManager, I18nResolver resolver,
            SettingsManager settingsManager, PluginLicenseManager licenseManager,
            UserAccessor userAccessor, FormatSettingsManager formatSettingsManager,
            LocaleManager localeManager, TemplateRenderer renderer,
            PageBuilderService pageBuilderService, ActiveObjects ao) {

        super(userManager, resolver, settingsManager,
                licenseManager, userAccessor, formatSettingsManager, localeManager,
                renderer, pageBuilderService, ao);
    }
}