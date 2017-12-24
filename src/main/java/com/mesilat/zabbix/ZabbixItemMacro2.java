package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixItem;
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
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.mesilat.format.CompiledFormat;
import com.mesilat.format.ItemFormat;
import com.mesilat.zabbix.client.ZabbixClient;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZabbixItemMacro2 extends ZabbixMacroBase implements Macro {
    public static final String FORMAT_SHORT  = "short";
    public static final String FORMAT_MEDIUM = "medium";
    public static final String FORMAT_LONG   = "long";

    private final ActiveObjects ao;

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

        if (params.containsKey("itemid")){
            return executeLegacy(userKey, params, body, conversionContext);
        } else {
            return execute(userKey, params, body, conversionContext);
        }
    }
    private String execute(UserKey userKey, Map params, String body, ConversionContext conversionContext) throws MacroExecutionException{
        if (params.get("item") == null){
            throw new MacroExecutionException(getResolver().getText("com.mesilat.zabbix-plugin.error.mandatory-params-not-found"));
        }
        String itemText = params.get("item").toString();
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
            try {
                ZabbixConnectionDescriptor conn = getConnection(userKey, Integer.parseInt(server));
                ZabbixClient client = getClient(conn);
                ZabbixItem item = client.getItemByKey(host, itemText, true);
                if (item == null){
                    return renderException(new Exception(getResolver().getText("com.mesilat.zabbix-plugin.error.item-not-found")));
                } else {
                    Locale locale = getLocale(userKey);
                    String format = getFormat(userKey, (String)params.get("format"));
                    if (format == null){
                        throw new MacroExecutionException(String.format("Failed to interpret format %s", params.get("format")));
                    }
                    CompiledFormat cf = CompiledFormat.compile(format, getBaseUrl(), locale);
                    Map<String,Object> map = new HashMap<>();
                    map.put("itemText", cf.format(item));
                    return renderFromSoy("Mesilat.Zabbix.Templates.zabbixItemSync.soy", map);
                }
            } catch (ZabbixResourceException | ZabbixClientException | ParseException ex) {
                throw new MacroExecutionException(ex);
            }
        } else {
            Map<String,Object> map = new HashMap<>();
            map.put("server", server);
            map.put("host", host);
            map.put("item", itemText);
            String format = getFormat(userKey, (String)params.get("format"));
            if (format == null){
                throw new MacroExecutionException(String.format("Failed to interpret format %s", params.get("format")));
            } else {
                map.put("format", format);
            }
            map.put("licensed", isLicensed());
            map.put("token", TokenService.createToken(userKey));

            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixItem2.soy", map);
        }
    }
    private String executeLegacy(UserKey userKey, Map params, String body, ConversionContext conversionContext) throws MacroExecutionException{
        if ("preview".equals(conversionContext.getOutputType())) {
            Locale locale = getLocale(userKey);
            try {
                ZabbixConnectionDescriptor conn = getDefaultConnection();
                List<ZabbixItem> items = getClient(conn).getItems(new String[] { params.get("itemid").toString() });
                if (items.isEmpty()) {
                    throw new MacroExecutionException("Item not found");
                    //return renderException(new Exception(getResolver().getText("com.mesilat.zabbix-plugin.error.item-not-found")));
                    //return renderFromSoy("Mesilat.Zabbix.Templates.errorItemNotFound.soy", new HashMap<String,Object>());
                } else {
                    ZabbixItem item = items.get(0);
                    String format = CompiledFormat.getFormat(ao, userKey, (String)params.get("format"));
                    CompiledFormat cf = CompiledFormat.compile(format, getBaseUrl(), locale);
                    Map<String,Object> map = new HashMap<>();
                    map.put("itemText", cf.format(item));
                    return renderFromSoy("Mesilat.Zabbix.Templates.zabbixItemSync.soy", map);
                }
            } catch(ZabbixResourceException | ZabbixClientException | ParseException ex) {
                throw new MacroExecutionException(ex);
                //return renderException(ex);
            }
        } else {
            Map<String,Object> map = new HashMap<>();
            map.put("itemId", params.get("itemid"));
            map.put("format", params.get("format") == null? "": params.get("format"));
            map.put("licensed", isLicensed());
            map.put("token", TokenService.createToken(userKey));

            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixItem.soy", map);
        }
    }
    private String getFormat(UserKey userKey, String format){
        if (format == null){
            return getDefaultFormat(userKey);
        }
        if (CompiledFormat.isValidFormat(format)){
            return format;
        }
        Matcher matcher = PATTERN_NAMED.matcher(format);
        if (matcher.matches() && CompiledFormat.isValidFormat(matcher.group(2))){
            return matcher.group(2);
        }
        return getFormatForName(userKey, format);
    }
    private String getFormatForName(UserKey userKey, String name){
        ItemFormat itemFormat = ao.executeInTransaction(()->{
            ItemFormat[] formats = ao.find(ItemFormat.class, "NAME = ? and OWNER_KEY = ?", name, userKey.getStringValue());
            if (formats.length > 0){
                return formats[0];
            }
            formats = ao.find(ItemFormat.class, "NAME = ? and OWNER_KEY IS NULL", name);
            if (formats.length > 0){
                return formats[0];
            }
            return null;
        });
        return itemFormat == null? null: itemFormat.getFormat();
    }
    private String getDefaultFormat(UserKey userKey){
        String format = getFormatForName(userKey, "default");
        return format == null? LegacyConfigService.DEFAULT_FORMAT: format;
    }
    
    public ZabbixItemMacro2(UserManager userManager, I18nResolver resolver,
            SettingsManager settingsManager, PluginLicenseManager licenseManager,
            UserAccessor userAccessor, FormatSettingsManager formatSettingsManager,
            LocaleManager localeManager, TemplateRenderer renderer,
            PageBuilderService pageBuilderService, ActiveObjects ao) {

        super(userManager, resolver, settingsManager, licenseManager,
                userAccessor, formatSettingsManager, localeManager, renderer,
                pageBuilderService, ao);

        this.ao = ao;
    }

    public static Pattern PATTERN_NAMED = Pattern.compile("\"(.+)\" (.+)");
}