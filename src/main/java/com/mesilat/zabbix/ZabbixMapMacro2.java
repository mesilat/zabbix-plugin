package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixClientException;
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
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import java.util.HashMap;
import java.util.Map;

public class ZabbixMapMacro2 extends ZabbixMacroBase implements Macro {
    private final ImageService imageService;

    @Override
    public TokenType getTokenType(Map parameters, String body, RenderContext context) {
        return TokenType.BLOCK;
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
        return _execute(params, body);
    }
    private String _execute(Map params, String body) throws MacroExecutionException {
        final String server = params.get("server").toString();
        final String map = params.get("map").toString();
        final String severity = params.get("severity") == null? null: params.get("severity").toString();

        String version = null;
        try {            
           version = imageService.getVersion(server);
        } catch(ZabbixClientException ex) {
            LOGGER.warn("Could not get Zabbix server version", ex);
        } catch (ZabbixResourceException ex) {
            LOGGER.warn("Could not get Zabbix server version", ex);
            throw new MacroExecutionException(ex.getMessage());
        }

        Map<String,Object> _map = new HashMap<>();
        _map.put("server", server);
        _map.put("map", map);
        _map.put("severity", severity);
        _map.put("altText", body);
        _map.put("licensed", isLicensed());
        if (version != null && ZabbixConnectionDescriptor.compareVersions(version, "3.4.0") >= 0){
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixSvgMap.soy", _map);
        } else {
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixMap.soy", _map);
        }
    }

    public ZabbixMapMacro2(UserManager userManager, I18nResolver resolver,
            SettingsManager settingsManager, PluginLicenseManager licenseManager,
            UserAccessor userAccessor, FormatSettingsManager formatSettingsManager,
            LocaleManager localeManager, TemplateRenderer renderer,
            PageBuilderService pageBuilderService, ActiveObjects ao,
            ImageService imageService
    ){
        super(userManager, resolver,
            settingsManager, licenseManager,
            userAccessor, formatSettingsManager,
            localeManager, renderer,
            pageBuilderService, ao);

        this.imageService = imageService;
    }
}