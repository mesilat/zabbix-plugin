package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import com.mesilat.util.StreamUtil;
import static com.mesilat.zabbix.ZabbixResourceBase.unscramble;
import com.mesilat.zabbix.client.ZabbixClient;
import com.mesilat.zabbix.client.ZabbixClientException;
import com.mesilat.zabbix.client.ZabbixGraph;
import com.mesilat.zabbix.client.ZabbixMap;
import java.io.ByteArrayOutputStream;
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
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.InitializingBean;

@ExportAsService ({ImageService.class})
@Named
public class ImageServiceImpl extends ZabbixResourceBase implements InitializingBean, ImageService {
    private final Map<Integer,String> cookies = new HashMap<>();
    private final Map<String,String> versions = new HashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        synchronized(cookies){
            cookies.clear();
        }
    }
    @Override
    public byte[] getImage(String server, String host, String graph, String width, String height, String period) throws ZabbixResourceException, ZabbixClientException {
        LOGGER.debug(String.format("Get Zabbix Graph at server %s, host %s, graph %s", server, host, graph));

        try (CloseableHttpClient client = createHttpClient()){
            ConfluenceUser user = AuthenticatedUserThreadLocal.get();
            if (user == null){
                throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication required");
            }
            ZabbixConnectionDescriptor conn = getConnection(user.getKey(), Integer.parseInt(server));
            ZabbixClient zc = getClient(conn);
            List<ZabbixGraph> graphs = zc.findGraphs(host, graph);
            if (graphs.isEmpty()){
                LOGGER.warn(String.format("Zabbix Graph could not be found at server %s, host %s, graph %s", conn.getUrl(), host, graph));
                throw new ZabbixResourceException(Response.Status.NOT_FOUND, "Graph not found");
            }

            String cookie = null;
            synchronized(cookies){
                if (cookies.containsKey(conn.getID())){
                    cookie = cookies.get(conn.getID());
                }
            }
            if (cookie == null){
                cookie = login(client, conn);
                if (cookie == null){
                    throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication with Zabbix server failed");
                }
            }
            synchronized(cookies){
                cookies.put(conn.getID(), cookie);
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
                .append(period)
                // for latest Zabbix server
                .append("&from=now-")
                .append(period)
                .append("s&to=now")
                .append("&profileIdx=web.graphs.filter");
            url = sb.toString();



            try {
                return getImage(client, url, cookie);
            } catch(ZabbixClientException ex){
                if (ex.getErrorCode() == HttpServletResponse.SC_UNAUTHORIZED){
                    LOGGER.debug("Session cookie invalid; requires relogin");
                    cookie = login(client, conn);
                    if (cookie == null){
                        throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication with Zabbix server failed");
                    }
                    synchronized(cookies){
                        cookies.put(conn.getID(), cookie);
                    }
                    return getImage(client, url, cookie);
                } else {
                    throw ex;
                }
            }
        }   catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException ex) {
            LOGGER.error("Unexpected exception", ex);
            throw new ZabbixResourceException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    @Override
    public byte[] getImage(String graphId, String width, String height, String period) throws ZabbixResourceException, ZabbixClientException {
        LOGGER.debug(String.format("LEGACY: Get Zabbix Graph %s, default server", graphId));

        try (CloseableHttpClient client = createHttpClient()){
            ZabbixConnectionDescriptor conn = getDefaultConnection();
            String cookie = null;
            synchronized(cookies){
                if (cookies.containsKey(conn.getID())){
                    cookie = cookies.get(conn.getID());
                }
            }
            if (cookie == null){
                cookie = login(client, conn);
                if (cookie == null){
                    throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication with Zabbix server failed");
                }
            }
            synchronized(cookies){
                cookies.put(conn.getID(), cookie);
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
                .append(period)
                // for latest Zabbix server
                .append("&from=now-")
                .append(period)
                .append("s&to=now")
                .append("&profileIdx=web.graphs.filter");
            url = sb.toString();



            try {
                return getImage(client, url, cookie);
            } catch(ZabbixClientException ex){
                if (ex.getErrorCode() == HttpServletResponse.SC_UNAUTHORIZED){
                    LOGGER.debug("Session cookie invalid; requires relogin");
                    cookie = login(client, conn);
                    if (cookie == null){
                        throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication with Zabbix server failed");
                    }
                    synchronized(cookies){
                        cookies.put(conn.getID(), cookie);
                    }
                    return getImage(client, url, cookie);
                } else {
                    throw ex;
                }
            }
        }   catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException ex) {
            LOGGER.error("Unexpected exception", ex);
            throw new ZabbixResourceException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    @Override
    public byte[] getMapImage(String server, String map, String severity) throws ZabbixResourceException, ZabbixClientException {
        LOGGER.debug(String.format("Get Zabbix Map at server %s, %s severity %s", server, map, severity));

        try (CloseableHttpClient client = createHttpClient()){
            ConfluenceUser user = AuthenticatedUserThreadLocal.get();
            if (user == null){
                throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication required");
            }
            ZabbixConnectionDescriptor conn = getConnection(user.getKey(), Integer.parseInt(server));
            ZabbixClient zc = getClient(conn);
            List<ZabbixMap> maps = zc.findMaps(map);
            if (maps.isEmpty()){
                LOGGER.warn(String.format("Zabbix Map could not be found at server %s, %s", conn.getUrl(), map));
                throw new ZabbixResourceException(Response.Status.NOT_FOUND, "Map not found");
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

            String cookie = null;
            synchronized(cookies){
                if (cookies.containsKey(conn.getID())){
                    cookie = cookies.get(conn.getID());
                }
            }
            if (cookie == null){
                cookie = login(client, conn);
                if (cookie == null){
                    throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication with Zabbix server failed");
                }
            }
            synchronized(cookies){
                cookies.put(conn.getID(), cookie);
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



            try {
                return getImage(client, url, cookie);
            } catch(ZabbixClientException ex){
                if (ex.getErrorCode() == HttpServletResponse.SC_UNAUTHORIZED){
                    LOGGER.debug("Session cookie invalid; requires relogin");
                    cookie = login(client, conn);
                    if (cookie == null){
                        throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication with Zabbix server failed");
                    }
                    synchronized(cookies){
                        cookies.put(conn.getID(), cookie);
                    }
                    return getImage(client, url, cookie);
                } else {
                    throw ex;
                }
            }
        }   catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException ex) {
            LOGGER.error("Unexpected exception", ex);
            throw new ZabbixResourceException(Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    @Override
    public String getVersion(String server) throws ZabbixResourceException, ZabbixClientException {
        synchronized(versions){
            if (versions.containsKey(server)){
                return versions.get(server);
            }
        }

        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null){
            throw new ZabbixResourceException(Status.UNAUTHORIZED, "Authentication required");
        }
        ZabbixConnectionDescriptor conn = getConnection(user.getKey(), Integer.parseInt(server));
        ZabbixClient client = new ZabbixClient(conn.getUrl());
        client.connect(conn.getUsername(), unscramble(conn.getPassword()));
        String version = client.getVersion();
        synchronized(versions){
            if (versions.containsKey(server)){
                return versions.put(server, version);
            }
        }
        return version;
    }

    private byte[] getImage(CloseableHttpClient client, String url, String cookie) throws IOException, ZabbixClientException{
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

        if (!verifyCookie(resp, cookie)){
            LOGGER.debug("Invalid connection description: Zabbix server rejected our authentication");
            throw new ZabbixClientException(HttpServletResponse.SC_UNAUTHORIZED, "Invalid connection description: Zabbix server rejected our authentication");
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = resp.getEntity().getContent()){
            StreamUtil.copy(in, buf);
        }
        LOGGER.debug(String.format("Chart request %s returned %d byte buffer", url, buf.size()));
        return buf.toByteArray();
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
    public ImageServiceImpl(I18nResolver resolver, UserManager userManager, ActiveObjects ao) {
        super(resolver, userManager, null, null, null, null, ao, null);
    }
}