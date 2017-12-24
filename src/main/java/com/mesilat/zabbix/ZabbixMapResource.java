package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixMap;
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

@Path("/map")
public class ZabbixMapResource extends ZabbixResourceBase {
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@QueryParam("server") Integer server, @QueryParam("q") String query, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        try {
            if (server == null){
                return Response.status(Response.Status.BAD_REQUEST).entity("Mandatory parameter(s) are missing").build();
            } else {
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                if (conn == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return findMaps(conn, query);
            }
        } catch(ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }
    public Response findMaps(ZabbixConnectionDescriptor conn, String query) throws ZabbixResourceException {
        LOGGER.debug("Lookup Zabbix maps for query: " + query);
        try {
            ZabbixClient client = getClient(conn);
            JSONArray arr = new JSONArray();
            for (ZabbixMap map : client.findMaps(query)) {
                JSONObject obj = new JSONObject();
                obj.put("id", map.getId());
                obj.put("name", map.getName());
                arr.put(obj);
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    public ZabbixMapResource(I18nResolver resolver, UserManager userManager, ActiveObjects ao) {
        super(resolver, userManager, null, null, null, null, ao, null);
    }
}