package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixItemRequest;
import com.mesilat.zabbix.client.ZabbixItem;
import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.mesilat.format.CompiledFormat;
import com.mesilat.zabbix.client.ZabbixClient;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/items")
public class ZabbixItemResource extends ZabbixResourceBase {
    private final ActiveObjects ao;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@QueryParam("server") Integer server, @QueryParam("host") String host, @QueryParam("item") String item, @QueryParam("q") String query, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

            try {
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                if (conn == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                if (host == null){
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            
                if (item == null){
                    return findItems(conn, host, query);
                } else {
                    return getItem(conn, host, item);
                }
            } catch(ZabbixResourceException ex){
                return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
            }
    }
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response post(final ZabbixItemRequest[] items, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        Locale locale = getLocale(userKey);

        try {
            if (items.length > 0){
                int server = items[0].getServer();
                for (ZabbixItemRequest item : items){
                    if (server != item.getServer()){
                        return Response.status(Response.Status.BAD_REQUEST).entity(getResolver().getText("com.mesilat.zabbix-plugin.error.servers-mismatch")).build();
                    }
                    if (!TokenService.isValidToken(userKey, item.getToken())){
                        return Response.status(Response.Status.FORBIDDEN).entity(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")).build();
                    }
                }
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                return getItems(conn, items, userKey, locale);
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity("No items requested").build();
            }
        } catch(ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }
    @Path("/legacy")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response post(final ZabbixItemRequestLegacy[] items, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        Locale locale = getLocale(userKey);

        try {
            if (items.length > 0){
                for (ZabbixItemRequestLegacy item : items){
                    if (!TokenService.isValidToken(userKey, item.getToken())){
                        return Response.status(Response.Status.FORBIDDEN).entity(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")).build();
                    }
                }
                ZabbixConnectionDescriptor conn = getDefaultConnection();
                return getItemsLegacy(conn, items, userKey, locale);
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity("No items requested").build();
            }
        } catch(ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }
    }

    private Response findItems(ZabbixConnectionDescriptor conn, String host, String query) throws ZabbixResourceException{
        LOGGER.debug("Lookup Zabbix items for host: " + host);
        try {
            ZabbixClient client = getClient(conn);
            JSONArray arr = new JSONArray();
            List<ZabbixItem> items = client.findItems(host, query);
            if (items.isEmpty()) {
                throw new ZabbixResourceException(
                    Response.Status.NOT_FOUND,
                    getResolver().getText("com.mesilat.zabbix-plugin.error.item.not-found")
                );
            }
            for (ZabbixItem item : items) {
                arr.put(item.toJsonObject());
            }
            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response.ok(results.toString()).build();
        } catch(ZabbixClientException | JSONException | ZabbixResourceException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    private Response getItem(ZabbixConnectionDescriptor conn, String host, String item) throws ZabbixResourceException{
        LOGGER.debug("Get Zabbix item for host: " + host);
        try {
            ZabbixClient client = getClient(conn);
            ZabbixItem zi = client.getItemByKey(host, item, false);
            if (zi == null) {
                throw new ZabbixResourceException(
                    Response.Status.NOT_FOUND,
                    getResolver().getText("com.mesilat.zabbix-plugin.error.item.not-found")
                );
            }
            return Response.ok(zi.toJsonObject().toString()).build();
        } catch(ZabbixClientException | JSONException | ZabbixResourceException ex) {
            throw new ZabbixResourceException(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    private Response getItems(ZabbixConnectionDescriptor conn, final ZabbixItemRequest[] itemRequests, UserKey userKey, Locale locale) throws ZabbixResourceException {
        try {
            JSONArray arr = new JSONArray();
            ZabbixClient client = getClient(conn);
            List<ZabbixItem> items = client.getItems(itemRequests);

            for (int i = 0; i < itemRequests.length; i++) {
                ZabbixItem item = items.get(i);
                if (item == null){
                    arr.put((Object)null);
                } else {
                    try {
                        String format = CompiledFormat.getFormat(ao, userKey, itemRequests[i].getFormat());
                        CompiledFormat cf = CompiledFormat.compile(format, getBaseUrl(), locale);
                        arr.put(cf.format(item));
                    } catch(ParseException ex) {
                        arr.put(new StringBuilder()
                            .append("<img src='")
                            .append(getBaseUrl())
                            .append("/download/resources/com.mesilat.zabbix-plugin/images/pluginIconErr.png'/> ")
                            .append(StringEscapeUtils.escapeHtml(ex.getLocalizedMessage()))
                            .toString()
                        );
                    }
                }
            }

            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response
                .ok(results.toString())
                .build();
        } catch (ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(
                Response.Status.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                ex
            );
        }
    }
    private Response getItemsLegacy(ZabbixConnectionDescriptor conn, final ZabbixItemRequestLegacy[] itemRequests, UserKey userKey, Locale locale) throws ZabbixResourceException {
        try {
            JSONArray arr = new JSONArray();
            ZabbixClient client = getClient(conn);
            String[] itemIds = new String[itemRequests.length];
            for (int i = 0; i < itemRequests.length; i++){
                itemIds[i] = itemRequests[i].getItemId();
            }
            List<ZabbixItem> items = client.getItems(itemIds);
            for (int i = 0; i < itemRequests.length; i++) {
                ZabbixItem item = items.get(i);
                if (item == null){
                    arr.put((Object)null);
                } else {
                    try {
                        String format = CompiledFormat.getFormat(ao, userKey, itemRequests[i].getFormat());
                        CompiledFormat cf = CompiledFormat.compile(format, getBaseUrl(), locale);
                        arr.put(cf.format(item));
                    } catch(ParseException ex) {
                        arr.put(new StringBuilder()
                            .append("<img src='")
                            .append(getBaseUrl())
                            .append("/download/resources/com.mesilat.zabbix-plugin/images/pluginIconErr.png'/> ")
                            .append(StringEscapeUtils.escapeHtml(ex.getLocalizedMessage()))
                            .toString()
                        );
                    }
                }
            }

            JSONObject results = new JSONObject();
            results.put("results", arr);
            return Response
                .ok(results.toString())
                .build();
        } catch (ZabbixClientException | JSONException ex) {
            throw new ZabbixResourceException(
                Response.Status.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                ex
            );
        }
    }

    public ZabbixItemResource(I18nResolver resolver, UserManager userManager,
            PluginLicenseManager licenseManager, UserAccessor userAccessor,
            FormatSettingsManager formatSettingsManager, LocaleManager localeManager,
            SettingsManager settingsManager, ActiveObjects ao) {
        super(resolver, userManager, licenseManager, userAccessor,
            formatSettingsManager, localeManager, ao, settingsManager);
        this.ao = ao;
    }
}