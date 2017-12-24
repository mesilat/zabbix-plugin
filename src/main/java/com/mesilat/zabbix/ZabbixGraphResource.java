package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixGraph;
import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.mesilat.zabbix.client.ZabbixClient;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/graph")
public class ZabbixGraphResource extends ZabbixResourceBase {
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    public Response get(
        @QueryParam("server") Integer server, @QueryParam("host") String host,
        @QueryParam("q") String query,
        @Context HttpServletRequest request
    ){
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        try {
            if (server == null){
                return getGraphsForSelect2(request.getParameter("hostid"));
            } else {
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                if (conn == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return findGraphs(conn, host, query);
            }
        } catch(ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }

    public Response findGraphs(ZabbixConnectionDescriptor conn, String host, String query) throws ZabbixResourceException {
        LOGGER.debug("Lookup Zabbix graphs for host: " + host);
        try {
            ZabbixClient client = getClient(conn);
            JSONArray arr = new JSONArray();
            List<ZabbixGraph> graphs = client.findGraphs(host, query);
            if (graphs.isEmpty()) {
                throw new ZabbixResourceException(
                    Response.Status.NOT_FOUND,
                    getResolver().getText("com.mesilat.zabbix-plugin.error.graph.not-found")
                );
            }
            for (ZabbixGraph graph : graphs) {
                arr.put(graph.toJsonObject());
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    public Response getGraphsForSelect2(String hostId) throws ZabbixResourceException {
        LOGGER.debug("Lookup Zabbix graphs for host: " + hostId);
        try {
            ZabbixClient client = getClient();
            JSONArray arr = new JSONArray();
            List<ZabbixGraph> graphs = client.getGraphs(hostId);
            if (graphs.isEmpty()) {
                throw new ZabbixResourceException(
                    Response.Status.NOT_FOUND,
                    getResolver().getText("com.mesilat.zabbix-plugin.error.graph.not-found")
                );
            }
            for (ZabbixGraph graph : graphs) {
                JSONObject obj = new JSONObject();
                obj.put("id", graph.getGraphId());
                obj.put("text", graph.getName());
                arr.put(obj);
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    public ZabbixGraphResource(I18nResolver resolver, UserManager userManager,
            ActiveObjects ao, SettingsManager settingsManager) {
        super(resolver, userManager, null, null, null, null, ao, settingsManager);
    }
}