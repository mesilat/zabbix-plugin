package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixMap;
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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.mesilat.util.StreamUtil;
import com.mesilat.zabbix.client.ZabbixClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import javax.ws.rs.core.Response;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

public class ZabbixMapMacro2 extends ZabbixMacroBase implements Macro {
    private final ActiveObjects ao;

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
        //if (params.containsKey("synchronous") && "true".equalsIgnoreCase(params.get("synchronous").toString())) {
        //    return executeSync(params, body, conversionContext);
        //} else {
            return executeAsync(params, body, conversionContext);
        //}
    }
    private String executeAsync(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        final UserKey userKey = getUserManager().getRemoteUserKey();
        final String server = params.get("server").toString();
        final String map = params.get("map").toString();

        final File path;
        try {
            path = TempFileService.createTempFile();
        } catch(IOException ex) {
            throw new MacroExecutionException("Failed to create temporary file", ex);
        }

        String version = null;
        try {
            
            ZabbixConnectionDescriptor conn = getConnection(userKey, Integer.parseInt(server));
            version = conn.getVersion();
            if (version == null){
                ZabbixClient client = new ZabbixClient(conn.getUrl());
                client.connect(conn.getUsername(), unscramble(conn.getPassword()));
                conn.setVersion(version = client.getVersion());
                ao.executeInTransaction(() -> {
                    conn.save();
                    return null;
                });
            }
        } catch(ZabbixClientException ex) {
            LOGGER.warn("Could not get Zabbix server version", ex);
        } catch (ZabbixResourceException ex) {
            throw new MacroExecutionException(String.format("Connection description for server %d not found", server), ex);
        }

        Thread t = new Thread(() -> {
            try {
                byte[] image = getImage(userKey, Integer.parseInt(server), map, (String)params.get("severity"));
                try (OutputStream out = new FileOutputStream(path)) {
                    out.write(image);
                }
            } catch(IOException | IllegalStateException | NoSuchAlgorithmException | KeyStoreException
                | KeyManagementException | ZabbixResourceException | ZabbixClientException ex) {
                LOGGER.warn("Failed to get zabbix chart", ex);
            }
        });
        t.start();

        Map<String,Object> _map = new HashMap<>();
        _map.put("fileName", path.getName());
        _map.put("altText", body);
        _map.put("licensed", isLicensed());
        if (version != null && ZabbixConnectionDescriptor.compareVersions(version, "3.4.0") >= 0){
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixSvgMap.soy", _map);
        } else {
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixMap.soy", _map);
        }
    }
    private String executeSync(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        final UserKey userKey = getUserManager().getRemoteUserKey();
        final String server = params.get("server").toString();
        final String map = params.get("map").toString();

        try {
            byte[] image = getImage(userKey, Integer.parseInt(server), map, (String)params.get("severity"));

            Map<String,Object> _map = new HashMap<>();
            _map.put("imgBase64", Base64.encodeBase64String(image));
            _map.put("altText", body);
            _map.put("licensed", isLicensed());
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixMapSync.soy", _map);
        } catch (IOException | ZabbixResourceException | ZabbixClientException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            throw new MacroExecutionException(ex);
        }
    }

    private byte[] getImage(UserKey userKey, int server, String map, String severity)
            throws IOException, ZabbixResourceException, ZabbixClientException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        LOGGER.debug(String.format("Get Zabbix Map at server %d, %s severity %s", server, map, severity));

        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustAllStrategy());
        try (CloseableHttpClient client = HttpClients
            .custom()
            .setSSLSocketFactory(
                new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE)
            )
            .setDefaultCookieStore(new DummyCookieStore())
            .build()
        ){
            ZabbixConnectionDescriptor conn = getConnection(userKey, server);
            ZabbixClient zc = getClient(conn);
            List<ZabbixMap> maps = zc.findMaps(map);
            if (maps.isEmpty()){
                LOGGER.debug(String.format("Zabbix Map could not be found at server %s, %s", conn.getUrl(), map));
                throw new ZabbixResourceException(Response.Status.NOT_FOUND, "Map not found");
            } else {
                LOGGER.debug(String.format("Zabbix Map was found at server %s, %s", conn.getUrl(), map));
            }

            int severityMin = maps.get(0).getSeverityMin();
            if (severity != null){
                switch (severity) {
                    case "Not classified":
                        severityMin = 0;
                        break;
                    case "Information":
                        severityMin = 1;
                        break;
                    case "Warning":
                        severityMin = 2;
                        break;
                    case "Average":
                        severityMin = 3;
                        break;
                    case "High":
                        severityMin = 4;
                        break;
                    case "Disaster":
                        severityMin = 5;
                        break;
                    default:
                        break;
                }
            }

            String cookie;
            synchronized (semaphore){
                if (sessionCookie == null){
                    sessionCookie = login(client, conn);
                }
                cookie = sessionCookie;
            }

            StringBuilder sb = new StringBuilder();
            String url = conn.getUrl().endsWith("/")? conn.getUrl() : conn.getUrl() + "/";
            sb.append(url)
              .append("map.php?noedit=1&sysmapid=")
              .append(maps.get(0).getId())
              .append("&width=1")
              .append("&height=1")
              .append("&period=3600")
              .append("&severity_min=")
              .append(severityMin);
            url = sb.toString();

            HttpUriRequest req = org.apache.http.client.methods.RequestBuilder
                .get()
                .setUri(url)
                .addHeader("Cookie", cookie)
                .build();
            HttpResponse resp = client.execute(req);
            if (resp.getStatusLine().getStatusCode() != 200){
                LOGGER.debug(String.format("Map request %s resulted in error %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
                throw new ZabbixClientException(resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase());
            }

            if (verifyCookie(resp, cookie)){
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream in = resp.getEntity().getContent()){
                    StreamUtil.copy(in, buf);
                }
                LOGGER.debug(String.format("Map request %s returned %d byte buffer", url, buf.size()));
                return buf.toByteArray();
            }

            // RETRY
            LOGGER.debug("Session cookie invalid; requires login");
            synchronized (semaphore){
                sessionCookie = cookie = login(client, conn);
            }
            req = org.apache.http.client.methods.RequestBuilder
                .get()
                .setUri(url)
                .addHeader("Cookie", cookie)
                .build();
            resp = client.execute(req);
            if (resp.getStatusLine().getStatusCode() != 200){
                LOGGER.debug(String.format("Map request %s resulted in error %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
                throw new ZabbixClientException(resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase());
            }

            if (!verifyCookie(resp, cookie)){
                LOGGER.debug("Invalid connection description: Zabbix server rejected our authentication");
                throw new ZabbixClientException("Invalid connection description: Zabbix server rejected our authentication");
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = resp.getEntity().getContent()){
                StreamUtil.copy(in, buf);
            }
            LOGGER.debug(String.format("Chart request %s returned %d byte buffer", url, buf.size()));
            return buf.toByteArray();
        }
    }

    public ZabbixMapMacro2(UserManager userManager, I18nResolver resolver,
            SettingsManager settingsManager, PluginLicenseManager licenseManager,
            UserAccessor userAccessor, FormatSettingsManager formatSettingsManager,
            LocaleManager localeManager, TemplateRenderer renderer,
            PageBuilderService pageBuilderService, ActiveObjects ao
    ){
        super(userManager, resolver,
            settingsManager, licenseManager,
            userAccessor, formatSettingsManager,
            localeManager, renderer,
            pageBuilderService, ao);
        this.ao = ao;
    }

    public static class TrustAllStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    }
}