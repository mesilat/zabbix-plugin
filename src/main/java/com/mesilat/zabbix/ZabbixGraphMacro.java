package com.mesilat.zabbix;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.TokenType;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZabbixGraphMacro extends BaseMacro implements Macro {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    private final PluginSettingsFactory pluginSettingsFactory;
    private final I18nResolver resolver;
    private final SettingsManager settingsManager;

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
        final String graphId = params.get("graphid").toString();
        final String period = params.get("period") == null? "3600": params.get("period").toString();
        final String width = "preview".equals(conversionContext.getOutputType())? "350":
                (params.get("width") == null? "1000": params.get("width").toString());

        SecureRandom random = new SecureRandom();
        final String imageId = new BigInteger(130, random).toString(32);
        
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        Config config = new Config(settings);
        final List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("name", config.getUsername()));
        try {
            nvps.add(new BasicNameValuePair("password", PasswordEncryption.unscramble(config.getPassword())));
        } catch(Exception ex) {
            LOGGER.warn("Failed to unscramble password", ex);
            nvps.add(new BasicNameValuePair("password", config.getPassword()));
        }
        nvps.add(new BasicNameValuePair("enter", "Sign in"));
        nvps.add(new BasicNameValuePair("form_refresh", "0"));
        nvps.add(new BasicNameValuePair("autologin", "1"));
        final String url = config.getUrl().endsWith("/")? config.getUrl() : config.getUrl() + "/";

        Thread t = new Thread(new Runnable(){
            public void run() {
                CloseableHttpClient client = HttpClients.custom().build();
                try {
                    HttpUriRequest httpRequest = org.apache.http.client.methods.RequestBuilder
                        .post()
                        .setEntity(new UrlEncodedFormEntity(nvps))
                        .setUri(url + "index.php")
                        .build();
                    client.execute(httpRequest); // Sign in

                    httpRequest = org.apache.http.client.methods.RequestBuilder
                        .get()
                        .setUri((new StringBuilder())
                            .append(url)
                            .append("chart2.php?graphid=")
                            .append(graphId)
                            .append("&width=")
                            .append(width)
                            .append("&period=")
                            .append(period)
                            .toString())
                        .build();
                    HttpResponse response = client.execute(httpRequest);
                    if (response.getStatusLine().getStatusCode() == 200) {
                        OutputStream out = new FileOutputStream("/tmp/" + imageId + ".png");
                        try {
                            copy(response.getEntity().getContent(), out);
                        } finally {
                            out.close();
                        }
                    }
                } catch(IOException ex) {
                    LOGGER.warn("Failed to get zabbix chart", ex);
                } catch (IllegalStateException ex) {
                    LOGGER.warn("Failed to get zabbix chart", ex);
                } finally {
                    try {
                        client.close();
                    } catch(IOException ignore) {}
                }
            }
        });
        t.start();

        String baseUrl = settingsManager.getGlobalSettings().getBaseUrl();
        return (new StringBuilder())
            .append("<div class='wiki-content'><pre>")
            .append("<img src=\"")
            .append(baseUrl)
            .append("/rest/zabbix-plugin/1.0/graph?id=")
            .append(imageId)
            .append("\" alt=\"")
            .append(body == null? "Zabbix Graph": body.replace("\"", "'"))
            .append("\" />")
            .append("</pre></div>")
            .toString();
    }

    public ZabbixGraphMacro(PluginSettingsFactory pluginSettingsFactory, I18nResolver resolver, SettingsManager settingsManager) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.resolver = resolver;
        this.settingsManager = settingsManager;
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }
}
