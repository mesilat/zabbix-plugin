package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.mesilat.zabbix.client.ZabbixClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.node.ObjectNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/connection")
@Scanned
public class ZabbixConnectionResource extends ZabbixResourceBase {
    private final ActiveObjects ao;
    private final UserAccessor userAccessor;
    private final PermissionManager permissionManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@QueryParam("id") Integer id, @QueryParam("q") String query, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

        return ao.executeInTransaction(() -> {
            try {
                if (id != null){
                    ZabbixConnectionDescriptor connection = ao.get(ZabbixConnectionDescriptor.class, id);
                    if (hasPermissions(userKey, connection)){
                        return Response.ok(toObject(connection, connection.getGrants()).toString()).build();
                    }
                    return Response.status(Response.Status.NOT_FOUND).entity(
                        getResolver().getText("com.mesilat.zabbix-plugin.error.connection.not-found")
                    ).build();
                } else {
                    JSONArray arr = new JSONArray();
                    for (ZabbixConnectionDescriptor connection: ao.find(ZabbixConnectionDescriptor.class, "OWNER_KEY = ?", userKey.getStringValue())){
                        if (hasPermissions(userKey, connection)){
                            if (query == null || connection.getUrl().toUpperCase().contains(query.toUpperCase())){
                                arr.put(toObject(connection, connection.getGrants()));
                            }
                        }
                    }
                    for (ZabbixConnectionDescriptor connection: ao.find(ZabbixConnectionDescriptor.class, "OWNER_KEY IS NULL")){
                        if (hasPermissions(userKey, connection)){
                            if (query == null || connection.getUrl().toUpperCase().contains(query.toUpperCase())){
                                arr.put(toObject(connection, connection.getGrants()));
                            }
                        }
                    }
                    for (ZabbixConnectionDescriptor connection: ao.find(ZabbixConnectionDescriptor.class, "OWNER_KEY <> ?", userKey.getStringValue())){
                        if (hasPermissions(userKey, connection)){
                            if (query == null || connection.getUrl().toUpperCase().contains(query.toUpperCase())){
                                arr.put(toObject(connection, connection.getGrants()));
                            }
                        }
                    }
                    JSONObject results = new JSONObject();
                    results.put("results", arr);
                    return Response.ok(results.toString()).build();
                }
            } catch (JSONException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            }
        });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response put(final ObjectNode obj, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

        if (obj.get("url") == null || obj.get("username") == null || obj.get("password") == null){
            return Response.status(Response.Status.BAD_REQUEST).entity(
                getResolver().getText("com.mesilat.zabbix-plugin.error.connection.mandatory-params-not-found")
            ).build();
        }

        return ao.executeInTransaction(() -> {
            try {
                ZabbixConnectionDescriptor connection;
                List<ZabbixConnectionGrant> grants = new ArrayList<>();

                if (obj.get("id") == null){
                    connection = ao.create(ZabbixConnectionDescriptor.class);
                    connection.setUrl(obj.get("url").getTextValue());
                    connection.setOwnerKey(userKey.getStringValue());
                    connection.setDefault("N");
                } else {
                    connection = ao.get(ZabbixConnectionDescriptor.class, obj.get("id").asInt());
                    if (!userKey.getStringValue().equals(connection.getOwnerKey())
                            && !getUserManager().isAdmin(userKey)
                    ){
                        return Response.status(Response.Status.FORBIDDEN).entity(
                            getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")
                        ).build();
                    }
                }

                connection.setUsername(obj.get("username").getTextValue());
                String password = obj.get("password").getTextValue();
                if (!isScrambled(password)){
                    password = scramble(password);
                }
                connection.setPassword(password);
                try {
                    testConnection(connection);
                } catch (Throwable ex) {
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                }
                connection.save();

                if (connection.getGrants() != null){
                    Arrays.asList(connection.getGrants()).forEach((grant)->{
                        ao.delete(grant);
                    });
                }

                if (obj.get("grantees") != null){
                    for (String grantee: obj.get("grantees").asText().split(",")){
                        ZabbixConnectionGrant grant = ao.create(ZabbixConnectionGrant.class);
                        grant.setConnectionDescriptor(connection);
                        grant.setGrantee(grantee);
                        grant.save();
                        grants.add(grant);
                    }
                }
                JSONObject results = new JSONObject();
                results.put("results", toObject(connection, grants.toArray(new ZabbixConnectionGrant[]{})));
                return Response.ok(results.toString()).build();
            } catch (JSONException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            }
        });
    }

    @DELETE
    public Response delete(@QueryParam("id") Integer id, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

        return ao.executeInTransaction(()->{
            ZabbixConnectionDescriptor connection = ao.get(ZabbixConnectionDescriptor.class, id);
            if (!userKey.getStringValue().equals(connection.getOwnerKey()) && !getUserManager().isAdmin(userKey)){
                return Response.status(Response.Status.FORBIDDEN).entity(
                    getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")
                ).build();
            }

            if (connection.getGrants() != null && connection.getGrants().length != 0){
                for (ZabbixConnectionGrant grant : connection.getGrants()){
                    ao.delete(grant);
                }
            }
            ao.delete(connection);
            return Response.ok().build();
        });
    }

    @POST
    public Response post(final Integer id, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        ConfluenceUser user = userAccessor.getUserByKey(userKey);
        
        if (!permissionManager.hasPermission(user, Permission.ADMINISTER, PermissionManager.TARGET_APPLICATION)){
            return Response.status(Response.Status.FORBIDDEN).entity(
                getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")
            ).build();
        }

        if (id == null){
            return Response.status(Response.Status.BAD_REQUEST).entity(
                getResolver().getText("com.mesilat.zabbix-plugin.error.connection.mandatory-params-not-found")
            ).build();
        }

        return ao.executeInTransaction(() -> {
            for (ZabbixConnectionDescriptor connection : ao.find(ZabbixConnectionDescriptor.class, "DEFAULT = 'Y'")){
                connection.setDefault("N");
                connection.save();
            }
            ZabbixConnectionDescriptor connection = ao.get(ZabbixConnectionDescriptor.class, id);
            connection.setDefault("Y");
            connection.save();
            return Response.ok().build();
        });
    }

    private JSONObject toObject(ZabbixConnectionDescriptor connection, ZabbixConnectionGrant[] grants) throws JSONException{
        JSONObject obj = new JSONObject();
        obj.put("id", connection.getID());
        obj.put("url", connection.getUrl());
        obj.put("username", connection.getUsername());
        obj.put("password", connection.getPassword());
        obj.put("default", "Y".equalsIgnoreCase(connection.getDefault()));

        if (connection.getOwnerKey() != null){
            UserProfile owner = getUserManager().getUserProfile(new UserKey(connection.getOwnerKey()));
            obj.put("ownerName", owner.getUsername());
            obj.put("ownerFullName", owner.getFullName());
        }
        if (grants != null && grants.length > 0){
            ArrayList<String> grantees = new ArrayList<>();
            JSONObject images = new JSONObject();
            for (ZabbixConnectionGrant grant : grants){
                grantees.add(grant.getGrantee());
                UserProfile userProfile = getUserManager().getUserProfile(grant.getGrantee());
                if (userProfile == null){
                    images.put(grant.getGrantee(), getBaseUrl() + "/images/icons/avatar_group_48.png");
                } else {
                    URI pictureUri = userProfile.getProfilePictureUri();
                    images.put(grant.getGrantee(), pictureUri == null? getBaseUrl() + "/images/icons/profilepics/default.png": pictureUri.toString());
                }
            }
            obj.put("grantees", StringUtils.join(grantees, ","));
            obj.put("images", images);
        } else {
            obj.put("grantees", "");
        }
        return obj;
    }
    public static void testConnection(ZabbixConnectionDescriptor connection) throws ZabbixClientException {
        ZabbixClient client = new ZabbixClient(connection.getUrl());
        client.connect(connection.getUsername(), unscramble(connection.getPassword()));
        connection.setVersion(client.getVersion());
    }
    private boolean hasPermissions(UserKey userKey, ZabbixConnectionDescriptor connection){
        if (getUserManager().isAdmin(userKey)){
            return true;
        }

        if (userKey.getStringValue().equals(connection.getOwnerKey())){
            return true;
        }

        if ("Y".equalsIgnoreCase(connection.getDefault())){
            return true;
        }

        for (ZabbixConnectionGrant grant : connection.getGrants()){
            UserProfile up = getUserManager().getUserProfile(grant.getGrantee());
            if (up != null && up.getUserKey().equals(userKey)){
                return true;
            }
            if (getUserManager().isUserInGroup(userKey, grant.getGrantee())){
                return true;
            }
        }
        return false;
    }

    public ZabbixConnectionResource(
        I18nResolver resolver, UserManager userManager,
        PluginLicenseManager licenseManager, UserAccessor userAccessor,
        FormatSettingsManager formatSettingsManager, LocaleManager localeManager,
        ActiveObjects ao, SettingsManager settingsManager,
        PermissionManager permissionManager
    ){
        super(resolver, userManager, licenseManager,
            userAccessor, formatSettingsManager, localeManager, ao, settingsManager
        );
        this.ao = ao;
        this.userAccessor = userAccessor;
        this.permissionManager = permissionManager;
    }
}