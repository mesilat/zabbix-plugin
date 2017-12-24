package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixGraph;
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
import com.mesilat.util.StreamUtil;
import com.mesilat.zabbix.client.ZabbixClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.util.Base64;

public class ZabbixGraphMacro2 extends ZabbixMacroBase implements Macro {
    
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
            return executeLegacy(params, body, conversionContext);
        } else {
            UserKey userKey = getUserManager().getRemoteUserKey();
            return execute(userKey, params, body, conversionContext);
        }
    }
    private String execute(UserKey userKey, Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
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

        if (params.containsKey("synchronous") && "true".equalsIgnoreCase(params.get("synchronous").toString())
            || "preview".equals(conversionContext.getOutputType())
        ){
            try {
                byte[] image = getImage(userKey, Integer.parseInt(server), host, graph, width, height, period);
                Map<String,Object> map = new HashMap<>();
                map.put("imgBase64", Base64.getEncoder().encodeToString(image));
                map.put("title", body);
                map.put("licensed", isLicensed());
                return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraphSync.soy", map);
            } catch (IOException | ZabbixResourceException | ZabbixClientException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
                throw new MacroExecutionException(ex);
            }
        } else {
            final File path;
            try {
                path = TempFileService.createTempFile();
            } catch(IOException ex) {
                throw new MacroExecutionException("Failed to create temporary file", ex);
            }

            Thread t = new Thread(() -> {
                try {
                    byte[] image = getImage(userKey, Integer.parseInt(server), host, graph, width, height, period);
                    try (OutputStream out = new FileOutputStream(path)) {
                        out.write(image);
                    }
                } catch(IOException | IllegalStateException | NoSuchAlgorithmException | KeyStoreException
                    | KeyManagementException | ZabbixResourceException | ZabbixClientException ex) {
                    LOGGER.warn("Failed to get zabbix chart", ex);
                }
            });
            t.start();

            Map<String,Object> map = new HashMap<>();
            map.put("fileName", path.getName());
            map.put("title", body);
            map.put("licensed", isLicensed());
            return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraph.soy", map);
        }
    }
    private String executeLegacy(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        final String graphId = params.get("graphid").toString();
        final String period = params.get("period") == null? "3600": params.get("period").toString();
        final String width = "preview".equals(conversionContext.getOutputType())? "350":
                (params.get("width") == null? "1000": params.get("width").toString());
        final String height = "preview".equals(conversionContext.getOutputType())? "175":
                (params.get("height") == null? "500": params.get("height").toString());

        final File path;
        try {
            path = TempFileService.createTempFile();
        } catch(IOException ex) {
            throw new MacroExecutionException("Failed to create temporary file", ex);
        }

        Thread t = new Thread(() -> {
            try {
                byte[] image = getImage(graphId, width, height, period);
                try (OutputStream out = new FileOutputStream(path)) {
                    out.write(image);
                }
            } catch(IOException | IllegalStateException | NoSuchAlgorithmException | KeyStoreException
                | KeyManagementException | ZabbixResourceException | ZabbixClientException ex) {
                LOGGER.warn("Failed to get zabbix chart", ex);
            }
        });
        t.start();

        Map<String,Object> map = new HashMap<>();
        map.put("fileName", path.getName());
        map.put("title", body);
        map.put("licensed", isLicensed());
        return renderFromSoy("Mesilat.Zabbix.Templates.zabbixGraph.soy", map);
    }

    private byte[] getImage(UserKey userKey, int server, String host, String graph, String width, String height, String period)
            throws IOException, ZabbixResourceException, ZabbixClientException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        LOGGER.debug(String.format("Get Zabbix Graph at server %d, host %s, graph %s", server, host, graph));

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
            List<ZabbixGraph> graphs = zc.findGraphs(host, graph);
            if (graphs.isEmpty()){
                LOGGER.debug(String.format("Zabbix Graph could not be found at server %s, host %s, graph %s", conn.getUrl(), host, graph));
                throw new ZabbixResourceException(Response.Status.NOT_FOUND, "Graph not found");
            } else {
                LOGGER.debug(String.format("Zabbix Graph was found at server %s, host %s, graph %s", conn.getUrl(), host, graph));
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
              .append("chart2.php?graphid=")
              .append(graphs.get(0).getGraphId())
              .append("&width=")
              .append(width)
              .append("&height=")
              .append(height)
              .append("&period=")
              .append(period);
            url = sb.toString();

            HttpUriRequest req = org.apache.http.client.methods.RequestBuilder
                .get()
                .setUri(url)
                .addHeader("Cookie", cookie)
                .build();
            HttpResponse resp = client.execute(req);
            if (resp.getStatusLine().getStatusCode() != 200){
                LOGGER.debug(String.format("Chart request %s resulted in error %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
                throw new ZabbixClientException(resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase());
            }

            if (verifyCookie(resp, cookie)){
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream in = resp.getEntity().getContent()){
                    StreamUtil.copy(in, buf);
                }
                LOGGER.debug(String.format("Chart request %s returned %d byte buffer", url, buf.size()));
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
                LOGGER.debug(String.format("Chart request %s resulted in error %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
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
    private byte[] getImage(String graphId, String width, String height, String period)
            throws IOException, ZabbixResourceException, ZabbixClientException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        LOGGER.debug(String.format("Get Zabbix Graph with id=%s", graphId));

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
            ZabbixConnectionDescriptor conn = getDefaultConnection();
            if (conn == null){
                throw new ZabbixClientException(getResolver().getText("com.mesilat.zabbix-plugin.error.no-default-connection"));
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
              .append("chart2.php?graphid=")
              .append(graphId)
              .append("&width=")
              .append(width)
              .append("&height=")
              .append(height)
              .append("&period=")
              .append(period);
            url = sb.toString();

            HttpUriRequest req = org.apache.http.client.methods.RequestBuilder
                .get()
                .setUri(url)
                .addHeader("Cookie", cookie)
                .build();
            HttpResponse resp = client.execute(req);
            if (resp.getStatusLine().getStatusCode() != 200){
                LOGGER.debug(String.format("Chart request %s resulted in error %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
                throw new ZabbixClientException(resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase());
            }

            if (verifyCookie(resp, cookie)){
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream in = resp.getEntity().getContent()){
                    StreamUtil.copy(in, buf);
                }
                LOGGER.debug(String.format("Chart request %s returned %d byte buffer", url, buf.size()));
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
                LOGGER.debug(String.format("Chart request %s resulted in error %d: %s", url, resp.getStatusLine().getStatusCode(), resp.getStatusLine().getReasonPhrase()));
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

    public ZabbixGraphMacro2(UserManager userManager, I18nResolver resolver,
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
    }

    public static class TrustAllStrategy implements TrustStrategy {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    }
}