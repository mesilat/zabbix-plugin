package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.core.DateFormatter;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUserPreferences;
import com.atlassian.confluence.user.UserAccessor;
import com.mesilat.util.PasswordEncryption1;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.user.User;
import com.mesilat.zabbix.client.ZabbixClient;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.ws.rs.core.Response;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZabbixResourceBase {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");
    
    private final I18nResolver resolver;
    private final UserManager userManager;
    private final PluginLicenseManager licenseManager;
    private final UserAccessor userAccessor;
    private final FormatSettingsManager formatSettingsManager;
    private final LocaleManager localeManager;
    private final ActiveObjects ao;
    private final SettingsManager settingsManager;

    public I18nResolver getResolver() {
        return resolver;
    }
    public UserManager getUserManager() {
        return userManager;
    }

    public String getBaseUrl(){
        return settingsManager.getGlobalSettings().getBaseUrl();
    }
    protected ZabbixConnectionDescriptor getConnection(UserKey userKey, int server) throws ZabbixResourceException {
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
    protected boolean hasPermissions(UserKey userKey, ZabbixConnectionDescriptor connection){
        if (connection == null){
            return false;
        }
        if (userManager.isAdmin(userKey)){
            return true;
        }

        if (userKey.getStringValue().equals(connection.getOwnerKey())){
            return true;
        }
        if ("Y".equalsIgnoreCase(connection.getDefault())){
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
    public ZabbixConnectionDescriptor getDefaultConnection() throws ZabbixResourceException {
        ZabbixConnectionDescriptor conn = ao.executeInTransaction(()->{
            for (ZabbixConnectionDescriptor _conn : ao.find(ZabbixConnectionDescriptor.class, "DEFAULT = 'Y'")){
                return _conn;
            }
            return null;
        });
        if (conn == null){
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, getResolver().getText("com.mesilat.zabbix-plugin.error.no-default-connection"));
        } else {
            return conn;
        }
    }
    public ZabbixClient getClient(ZabbixConnectionDescriptor conn) throws ZabbixResourceException {
        try {
            ZabbixClient client = new ZabbixClient(conn.getUrl());
            client.connect(conn.getUsername(), unscramble(conn.getPassword()));
            return client;
        } catch(Exception ex) {
            LOGGER.warn("Failed to connect to Zabbix server", ex);
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
    public ZabbixClient getClient() throws ZabbixResourceException {
        try {
            ZabbixConnectionDescriptor conn = getDefaultConnection();
            ZabbixClient client = new ZabbixClient(conn.getUrl());
            client.connect(conn.getUsername(), unscramble(conn.getPassword()));
            return client;
        } catch(Exception ex) {
            LOGGER.warn("Failed to connect to Zabbix server", ex);
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
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
    protected boolean isLicensed() {
        try {
            return licenseManager.getLicense().get().isValid();
        } catch(Throwable ignore) {
            return false;
        }
    }
    protected CloseableHttpClient createHttpClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustAllStrategy());
        return HttpClients
            .custom()
            .setSSLSocketFactory(
                new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE)
            )
            .setDefaultCookieStore(new DummyCookieStore())
            .build();
    }

    
    protected static String unscramble(String password){
        try {
            return PasswordEncryption1.unscramble(password);
        } catch (Exception ignore) {
            return password;
        }
    }
    protected static String scramble(String password){
        try {
            return PasswordEncryption1.scramble(password);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    protected boolean isScrambled(String password){
        try {
            PasswordEncryption1.unscramble(password);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public ZabbixResourceBase(I18nResolver resolver, UserManager userManager,
            PluginLicenseManager licenseManager, UserAccessor userAccessor,
            FormatSettingsManager formatSettingsManager, LocaleManager localeManager,
            ActiveObjects ao, SettingsManager settingsManager
    ){
        this.resolver = resolver;
        this.userManager = userManager;
        this.licenseManager = licenseManager;
        this.userAccessor = userAccessor;
        this.formatSettingsManager = formatSettingsManager;
        this.localeManager = localeManager;
        this.ao = ao;
        this.settingsManager = settingsManager;
    }

    public static class TrustAllStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    }
}