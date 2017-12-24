package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixHost;
import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.mesilat.zabbix.client.ZabbixClient;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/host")
public class ZabbixHostResource extends ZabbixResourceBase {
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@QueryParam("server") Integer server, @QueryParam("q") String query, @QueryParam("host") String host, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        try {
            if (server == null){
                return findHosts(query);
            } else if (host != null) {
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                if (conn == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return getHost(conn, host);
            } else {
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                if (conn == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return findHosts(conn, query);
            }
        } catch(ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }

    public Response getHost(ZabbixConnectionDescriptor conn, String host) throws ZabbixResourceException {
        LOGGER.debug("Lookup Zabbix host: " + host);
        try {
            ZabbixClient client = getClient(conn);
            ZabbixHost _host = client.getHost(host);
            if (_host == null){
                throw new ZabbixResourceException(Response.Status.NOT_FOUND, "Host not found");
            }
            JSONObject obj = new JSONObject();
            obj.put("id", _host.getHost());
            obj.put("text", _host.getName());
            return Response.ok(obj.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    public Response findHosts(ZabbixConnectionDescriptor conn, String query) throws ZabbixResourceException {
        LOGGER.debug("Lookup Zabbix hosts for query: " + query);
        try {
            ZabbixClient client = getClient(conn);
            JSONArray arr = new JSONArray();
            for (ZabbixHost host : client.findHosts(query)) {
                JSONObject obj = new JSONObject();
                obj.put("id", host.getHost());
                obj.put("text", host.getName());
                arr.put(obj);
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    public Response findHosts(String name) throws ZabbixResourceException {
        LOGGER.debug("Lookup Zabbix hosts for query: " + name);
        try {
            ZabbixClient client = getClient();
            JSONArray arr = new JSONArray();
            for (ZabbixHost host : client.findHosts(name)) {
                JSONObject obj = new JSONObject();
                obj.put("id", host.getHostId());
                obj.put("text", host.getName());
                arr.put(obj);
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    
    public ZabbixHostResource(I18nResolver resolver, UserManager userManager, ActiveObjects ao) {
        super(resolver, userManager, null, null, null, null, ao, null);
    }
}