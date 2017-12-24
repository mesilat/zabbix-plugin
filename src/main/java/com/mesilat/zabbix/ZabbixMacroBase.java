package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.core.DateFormatter;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.renderer.template.TemplateRenderer;
import com.mesilat.util.PasswordEncryption1;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUserPreferences;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.user.User;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import com.mesilat.zabbix.client.ZabbixClient;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZabbixMacroBase extends BaseMacro {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    protected static String sessionCookie = null;
    protected static final Object semaphore = new Object();

    private final I18nResolver resolver;
    private final UserManager userManager;
    private final SettingsManager settingsManager;
    private final PluginLicenseManager licenseManager;
    private final UserAccessor userAccessor;
    private final FormatSettingsManager formatSettingsManager;
    private final LocaleManager localeManager;
    private final TemplateRenderer renderer;
    private final PageBuilderService pageBuilderService;
    private final ActiveObjects ao;

    public I18nResolver getResolver() {
        return resolver;
    }
    public UserManager getUserManager() {
        return userManager;
    }
    public PluginLicenseManager getLicenseManager() {
        return licenseManager;
    }

    protected boolean isLicensed() {
        try {
            return licenseManager.getLicense().get().isValid();
        } catch(Throwable ignore) {
            return false;
        }
    }
    public String getBaseUrl() {
        return settingsManager.getGlobalSettings().getBaseUrl();
    }
    public ZabbixConnectionDescriptor getDefaultConnection(){
        return ao.executeInTransaction(()->{
            for (ZabbixConnectionDescriptor conn : ao.find(ZabbixConnectionDescriptor.class, "DEFAULT = 'Y'")){
                return conn;
            }
            return null;
        });
    }
    public String blockNotAuthenticated() {
        return (new StringBuilder())
            .append("<div class='aui-message aui-message-error'>")
            .append("<p class='title'><strong>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.common.error"))
            .append("</strong></p><p>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authenticated"))
            .append("</p></div>")
            .toString();
    }
    public String blockNotAuthorized() {
        return (new StringBuilder())
            .append("<div class='aui-message aui-message-error'>")
            .append("<p class='title'><strong>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.common.error"))
            .append("</strong></p><p>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized"))
            .append("</p></div>")
            .toString();
    }
    public Locale getLocale(UserKey userKey) {
        User user = userAccessor.getUserByKey(userKey);
        return localeManager.getLocale(user);
    }
    public ConfluenceUserPreferences getPreferences(UserKey userKey) {
        return userAccessor.getConfluenceUserPreferences(
                userAccessor.getExistingUserByKey(userKey)
        );
    }
    public DateFormatter getDateFormatter(ConfluenceUserPreferences preferences) {
        return preferences.getDateFormatter(formatSettingsManager, localeManager);
    }
    public String renderFromSoy(String soyTemplate, Map soyContext) {
        StringBuilder output = new StringBuilder();
        pageBuilderService.assembler().resources().requireWebResource("com.mesilat.zabbix-plugin:macro-resources-css");
        renderer.renderTo(output, "com.mesilat.zabbix-plugin:macro-resources", soyTemplate, soyContext);
        return output.toString();
    }
    public String renderException(Exception ex) {
        Map<String,Object> map = new HashMap<>();
        map.put("errorText", ex.getMessage());
        return renderFromSoy("Mesilat.Zabbix.Templates.error.soy", map);
    }
    public ZabbixHostDefault getHostDefault(long pageId){
        return ao.executeInTransaction(()->{
            ZabbixHostDefault[] _hd = ao.find(ZabbixHostDefault.class, "PAGE_ID = ?", pageId);
            if (_hd.length > 0){
                return _hd[0];
            } else {
                return null;
            }
        });
    }

    protected ZabbixClient getClient(ZabbixConnectionDescriptor conn) throws ZabbixResourceException {
        try {
            ZabbixClient client = new ZabbixClient(conn.getUrl());
            client.connect(conn.getUsername(), unscramble(conn.getPassword()));
            return client;
        } catch(Exception ex) {
            LOGGER.warn("Failed to connect to Zabbix server", ex);
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
    protected ZabbixConnectionDescriptor getConnection(UserKey userKey, int server) throws ZabbixResourceException{
        ZabbixConnectionDescriptor conn = ao.executeInTransaction(()->{
            ZabbixConnectionDescriptor _conn = ao.get(ZabbixConnectionDescriptor.class, server);
            return hasPermissions(userKey, _conn)? _conn: null;
        });
        if (conn == null){
            throw new ZabbixResourceException(Response.Status.FORBIDDEN, getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized-to-server"));
        } else {
            return conn;
        }
    }
    private boolean hasPermissions(UserKey userKey, ZabbixConnectionDescriptor connection){
        if (connection == null){
            return false;
        }
        if ("Y".equalsIgnoreCase(connection.getDefault())){
            return true;
        }
        if (userManager.isAdmin(userKey)){
            return true;
        }

        if (userKey.getStringValue().equals(connection.getOwnerKey())){
            return true;
        }
        for (ZabbixConnectionGrant grant : connection.getGrants()){
            UserProfile up = userManager.getUserProfile(grant.getGrantee());
            if (up != null && up.getUserKey().equals(userKey)){
                return true;
            }
            if (userManager.isUserInGroup(userKey, grant.getGrantee())){
                return true;
            }
        }
        return false;
    }
    protected String unscramble(String password){
        try {
            return PasswordEncryption1.unscramble(password);
        } catch (Exception ignore) {
            return password;
        }
    }
    protected String login(CloseableHttpClient client, ZabbixConnectionDescriptor conn) throws UnsupportedEncodingException, IOException{
        final List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("name", conn.getUsername()));
        nvps.add(new BasicNameValuePair("password", unscramble(conn.getPassword())));
        nvps.add(new BasicNameValuePair("enter", "Sign in"));
        nvps.add(new BasicNameValuePair("form_refresh", "0"));
        nvps.add(new BasicNameValuePair("autologin", "1"));
        String url = conn.getUrl().endsWith("/")? conn.getUrl() : conn.getUrl() + "/";

        HttpUriRequest httpRequest = org.apache.http.client.methods.RequestBuilder
            .post()
            .setEntity(new UrlEncodedFormEntity(nvps))
            .setUri(url + "index.php")
            .build();

        Header[] setCookieHeaders;
        try (CloseableHttpResponse resp = client.execute(httpRequest)){
            LOGGER.debug(String.format("Sign in to %s resulted in %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
            setCookieHeaders = resp.getHeaders("Set-Cookie");
        }

        HttpCookie cookie = null;
        for (Header h : setCookieHeaders){
            List<HttpCookie> cookies = HttpCookie.parse(h.getValue());
            cookie = cookies.get(0);
        }
        return cookie.toString();
    }
    protected boolean verifyCookie(HttpResponse resp, String cookie){
        for (Header h : resp.getHeaders("Set-Cookie")){
            if (h.getValue().contains(cookie)){
                return true;
            }
        }
        return false;
    }

    public ZabbixMacroBase(UserManager userManager, I18nResolver resolver,
            SettingsManager settingsManager, PluginLicenseManager licenseManager,
            UserAccessor userAccessor, FormatSettingsManager formatSettingsManager,
            LocaleManager localeManager, TemplateRenderer renderer,
            PageBuilderService pageBuilderService, ActiveObjects ao) {
        this.userManager = userManager;
        this.resolver = resolver;
        this.settingsManager = settingsManager;
        this.licenseManager = licenseManager;
        this.userAccessor = userAccessor;
        this.formatSettingsManager = formatSettingsManager;
        this.localeManager = localeManager;
        this.renderer = renderer;
        this.pageBuilderService = pageBuilderService;
        this.ao = ao;
    }
}