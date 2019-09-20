package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.util.ConfluenceHomeGlobalConstants;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import static com.mesilat.zabbix.ZabbixResourceBase.unscramble;
import java.io.ByteArrayOutputStream;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;

@Named
public class StaticImageServlet extends HttpServlet {
    private static final String PREFIX = "BB94FA";
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    private final Map<Integer,String> cookies = new HashMap<>();
    private final ActiveObjects ao;
    private final File tempDir;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String _server = request.getParameter("server");
        if (_server == null){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Zabbix server not specified");
            return;
        }

        String _image = request.getParameter("image");
        if (_image == null){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Zabbix image not specified");
            return;
        }

        // First lookup in cache
        File file = new File(tempDir, String.format("%s-%s-%s.dat", PREFIX, _server, _image));
        File mime = new File(tempDir, String.format("%s-%s-%s.mime", PREFIX, _server, _image));
        if (file.exists() && file.isFile() && mime.exists() && mime.isFile()){
            String contentType = FileUtils.readFileToString(mime, "ASCII");
            byte[] data = FileUtils.readFileToByteArray(file);
            response.setContentType(contentType);
            response.getOutputStream().write(data, 0, data.length);
            return;
        }

        Integer server, image;
        try {
            server = Integer.parseInt(_server);
        } catch(NumberFormatException ignore){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Zabbix server identifier");
            return;
        }
        try {
            image = Integer.parseInt(_image);
        } catch(NumberFormatException ignore){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Zabbix image identifier");
            return;
        }


        try (CloseableHttpClient client = createHttpClient()){
            ZabbixConnectionDescriptor conn = getConnection(server);

            String cookie = null;
            synchronized(cookies){
                if (cookies.containsKey(conn.getID())){
                    cookie = cookies.get(conn.getID());
                }
            }
            if (cookie == null){
                cookie = login(client, conn);
                if (cookie == null){
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication with Zabbix server failed");
                    return;
                }
                synchronized(cookies){
                    cookies.put(conn.getID(), cookie);
                }
            }

            StringBuilder sb = new StringBuilder();
            String url = conn.getUrl().endsWith("/")? conn.getUrl() : conn.getUrl() + "/";
            sb.append(url)
                .append("imgstore.php?iconid=")
                .append(image);
            url = sb.toString();

            HttpUriRequest req = org.apache.http.client.methods.RequestBuilder
                .get()
                .setUri(url)
                .addHeader("Cookie", cookie)
                .build();
            HttpResponse resp = client.execute(req);

            if (!verifyCookie(resp, cookie)){
                // New login required
                cookie = login(client, conn);
                if (cookie == null){
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication with Zabbix server failed");
                    return;
                }
                synchronized(cookies){
                    cookies.put(conn.getID(), cookie);
                }

                req = org.apache.http.client.methods.RequestBuilder
                    .get()
                    .setUri(url)
                    .addHeader("Cookie", cookie)
                    .build();
                resp = client.execute(req);
            }

            response.setStatus(resp.getStatusLine().getStatusCode()/*, resp.getStatusLine().getReasonPhrase()*/);
            HttpEntity entity = resp.getEntity();
            if (entity != null){
                String contentType = entity.getContentType().getValue();
                if (contentType != null){
                    response.setContentType(entity.getContentType().getValue());
                    FileUtils.writeStringToFile(mime, contentType, "ASCII");
                }
                try (InputStream in = entity.getContent(); ByteArrayOutputStream buf = new ByteArrayOutputStream()){
                    IOUtils.copy(in, buf);
                    byte[] data = buf.toByteArray();
                    IOUtils.write(data, response.getOutputStream());
                    FileUtils.writeByteArrayToFile(file, data);
                }
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException ex) {
            LOGGER.error("Unexpected exception", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    private CloseableHttpClient createHttpClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new ZabbixResourceBase.TrustAllStrategy());
        return HttpClients
            .custom()
            .setSSLSocketFactory(
                new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE)
            )
            .setDefaultCookieStore(new DummyCookieStore())
            .build();
    }
    private ZabbixConnectionDescriptor getConnection(int server){
        return ao.get(ZabbixConnectionDescriptor.class, server);
    }
    private String login(CloseableHttpClient client, ZabbixConnectionDescriptor conn) throws UnsupportedEncodingException, IOException{
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
        return cookie == null? null: cookie.toString();
    }
    private boolean verifyCookie(HttpResponse resp, String cookie){
        for (Header h : resp.getHeaders("Set-Cookie")){
            if (h.getValue().contains(cookie)){
                return true;
            }
        }
        return false;
    }

    @Inject
    public StaticImageServlet(ActiveObjects ao, @ComponentImport BootstrapManager bootManager){
        this.ao = ao;
        this.tempDir = new File(bootManager.getLocalHome(), ConfluenceHomeGlobalConstants.TEMP_DIR);
    }
}