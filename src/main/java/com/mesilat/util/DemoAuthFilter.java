package com.mesilat.util;

import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.seraph.auth.DefaultAuthenticator;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class DemoAuthFilter implements Filter {
    private final UserAccessor userAccessor;
    public static final UserKey DEMO = new UserKey("2c9191d9552b051f01552ed4ef990003");
    
    // https://.../rest/prototype/1/search/user.json?max-results=1&query=demo
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)request;

        if (req.getSession().getAttribute(DefaultAuthenticator.LOGGED_IN_KEY) == null) {
            ConfluenceUser user = userAccessor.getUserByKey(DEMO);
            req.getSession().setAttribute(DefaultAuthenticator.LOGGED_IN_KEY, user);
            req.getSession().setAttribute(DefaultAuthenticator.LOGGED_OUT_KEY, null);
        }
        chain.doFilter(request, response);
    }
    @Override
    public void destroy() {        
    }
    @Override
    public void init(FilterConfig filterConfig) {        
    }
    
    public DemoAuthFilter(UserManager userManager, UserAccessor userAccessor) {
        this.userAccessor = userAccessor;
    }
}
