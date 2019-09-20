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
import java.util.Base64;

public class ZabbixGraphMacro2 extends ZabbixMacroBase implements Macro {
    private final ImageService imageService;

    @Override
    public TokenType getTokenType(Map parameters, String body, RenderContext context) {
        return TokenType.BLOCK;
    }
    @Override
    public boolean hasBody() {
        return true;
    }
    @Override
    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }
    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
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
        if (params.containsKey("graphid")){
            return _executeLegacy(params, body, conversionContext);
        } else {
            return _execute(params, body, conversionContext);
        }
    }
    private String _execute(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        if (params.get("graph") == null){
            throw new MacroExecutionException(getResolver().getText("com.mesilat.zabbix-plugin.error.mandatory-params-not-found"));
        }
        String graph = params.get("graph").toString();
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

        final String period = params.get("period") == null? "3600": params.get("period").toString();
        final String width = "preview".equals(conversionContext.getOutputType())? "350":
                (params.get("width") == null? "1000": params.get("width").toString());
        final String height = "preview".equals(conversionContext.getOutputType())? "175":
                (params.get("height") == null? "500": params.get("height").toString());

        if ("preview".equals(conversionContext.getOutputType())){
            try {
                byte[] image = imageService.getImage(server, host, graph, width, height, period);
                Map<String,Object> map = new HashMap<>();
                map.put("imgBase64", Base64.getEncoder().encodeToString(image));
                map.put("title", body);
                map.put("licensed", isLicensed());
                return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraphSync.soy", map);
            } catch (ZabbixResourceException | ZabbixClientException ex) {
                throw new MacroExecutionException(ex);
            }
        } else {
            Map<String,Object> map = new HashMap<>();
            map.put("server", server);
            map.put("host",   host);
            map.put("graph",  graph);
            map.put("period", period);
            map.put("width",  width);
            map.put("height", height);            
            map.put("licensed", isLicensed());
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraphAsync.soy", map);
        }
    }
    private String _executeLegacy(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        final String graphId = params.get("graphid").toString();
        final String period = params.get("period") == null? "3600": params.get("period").toString();
        final String width = "preview".equals(conversionContext.getOutputType())? "350":
                (params.get("width") == null? "1000": params.get("width").toString());
        final String height = "preview".equals(conversionContext.getOutputType())? "175":
                (params.get("height") == null? "500": params.get("height").toString());

        if ("preview".equals(conversionContext.getOutputType())){
            try {
                byte[] image = imageService.getImage(graphId, width, height, period);
                Map<String,Object> map = new HashMap<>();
                map.put("imgBase64", Base64.getEncoder().encodeToString(image));
                map.put("title", body);
                map.put("licensed", isLicensed());
                return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraphSync.soy", map);
            } catch (ZabbixResourceException | ZabbixClientException ex) {
                throw new MacroExecutionException(ex);
            }
        } else {
            Map<String,Object> map = new HashMap<>();
            map.put("graphid",graphId);
            map.put("period", period);
            map.put("width",  width);
            map.put("height", height);            
            map.put("licensed", isLicensed());
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraphLegacyAsync.soy", map);
        }
    }

    public ZabbixGraphMacro2(UserManager userManager, I18nResolver resolver,
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