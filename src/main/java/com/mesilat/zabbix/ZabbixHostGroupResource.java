package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.mesilat.zabbix.client.ZabbixClient;
import com.mesilat.zabbix.client.ZabbixHostGroup;
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

@Path("/host-group")
public class ZabbixHostGroupResource extends ZabbixResourceBase {
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@QueryParam("server") Integer server, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        try {
            if (server == null){
                return listHostGroups();
            } else {
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                if (conn == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return listHostGroups(conn);
            }
        } catch(ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }
    public Response listHostGroups(ZabbixConnectionDescriptor conn) throws ZabbixResourceException {
        LOGGER.debug("List Zabbix host groups");
        try {
            ZabbixClient client = getClient(conn);
            JSONArray arr = new JSONArray();
            for (ZabbixHostGroup hostGroup : client.listHostGroups()) {
                JSONObject obj = new JSONObject();
                obj.put("text", hostGroup.getName());
                arr.put(obj);
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    public Response listHostGroups() throws ZabbixResourceException {
        LOGGER.debug("List Zabbix host groups");
        try {
            ZabbixClient client = getClient();
            JSONArray arr = new JSONArray();
            for (ZabbixHostGroup hostGroup : client.listHostGroups()) {
                JSONObject obj = new JSONObject();
                obj.put("text", hostGroup.getName());
                arr.put(obj);
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    
    public ZabbixHostGroupResource(I18nResolver resolver, UserManager userManager, ActiveObjects ao) {
        super(resolver, userManager, null, null, null, null, ao, null);
    }
}