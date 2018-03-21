/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.ui.web.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.platform.ui.web.auth.DummyAuthPluginAnonymous.DUMMY_ANONYMOUS_LOGIN;
import static org.nuxeo.ecm.platform.ui.web.auth.DummyAuthPluginForm.DUMMY_AUTH_FORM_PASSWORD_KEY;
import static org.nuxeo.ecm.platform.ui.web.auth.DummyAuthPluginForm.DUMMY_AUTH_FORM_USERNAME_KEY;
import static org.nuxeo.ecm.platform.ui.web.auth.DummyAuthPluginSSO.DUMMY_SSO_TICKET;
import static org.nuxeo.ecm.platform.ui.web.auth.DummyAuthPluginToken.DUMMY_AUTH_TOKEN_KEY;
import static org.nuxeo.ecm.platform.ui.web.auth.NXAuthConstants.LOGIN_PAGE;
import static org.nuxeo.ecm.platform.ui.web.auth.NXAuthConstants.LOGOUT_PAGE;
import static org.nuxeo.ecm.platform.ui.web.auth.NXAuthConstants.REQUESTED_URL;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginContext;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ecm.platform.web.common:OSGI-INF/authentication-framework.xml")
public class TestNuxeoAuthenticationFilter {

    // from NuxeoAuthenticationFilter
    protected static final String BYPASS_AUTHENTICATION_LOG = "byPassAuthenticationLog";

    // from NuxeoAuthenticationFilter
    protected static final String SECURITY_DOMAIN = "securityDomain";

    // from NuxeoAuthenticationFilter
    protected static final String EVENT_LOGIN_SUCCESS = "loginSuccess";

    // from NuxeoAuthenticationFilter
    protected static final String EVENT_LOGOUT = "logout";

    @Mock
    @RuntimeService
    protected UserManager userManager;

    @Mock
    @RuntimeService
    protected EventProducer eventProducer;

    protected NuxeoAuthenticationFilter filter;

    protected DummyFilterChain chain;

    protected ArgumentCaptor<Event> eventCaptor;

    public static class DummyFilterConfig implements FilterConfig {

        protected final Map<String, String> initParameters;

        public DummyFilterConfig(Map<String, String> initParameters) {
            this.initParameters = initParameters;
        }

        @Override
        public String getFilterName() {
            return "NuxeoAuthenticationFilter";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }
    }

    public static class DummyFilterChain implements FilterChain {

        protected boolean called;

        protected Principal principal;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called = true;
            principal = ((HttpServletRequest) request).getUserPrincipal();
        }
    }

    @Before
    public void setUp() throws Exception {
        // filter config
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put(BYPASS_AUTHENTICATION_LOG, "false");
        initParameters.put(SECURITY_DOMAIN, NuxeoAuthenticationFilter.LOGIN_DOMAIN);
        FilterConfig config = new DummyFilterConfig(initParameters);
        // filter
        filter = new NuxeoAuthenticationFilter();
        filter.init(config);
        // filter chain
        chain = new DummyFilterChain();
        // TODO prefilters

        // usemanager
        when(userManager.getAnonymousUserId()).thenReturn(DUMMY_ANONYMOUS_LOGIN);
        // events
        eventCaptor = ArgumentCaptor.forClass(Event.class);
    }

    @After
    public void tearDown() {
        filter.destroy();
    }

    protected Map<String, Object> mockSessionAttributes(HttpSession session) {
        Map<String, Object> attributes = new HashMap<>();
        // getAttribute
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            return attributes.get(key);
        }).when(session).getAttribute(anyString());
        // setAttribute
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            Object value = i.getArguments()[1];
            attributes.put(key, value);
            return null;
        }).when(session).setAttribute(anyString(), any());
        // removeAttribute
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            attributes.remove(key);
            return null;
        }).when(session).removeAttribute(anyString());
        // invalidate
        doAnswer(i -> {
            attributes.clear();
            return null;
        }).when(session).invalidate();
        return attributes;
    }

    @SuppressWarnings("boxing")
    protected void mockRequestURI(HttpServletRequest request, String scheme, String host, int port, String contextPath,
            String servletPath, String pathInfo, String queryString) {
        if ("".equals(pathInfo)) {
            pathInfo = null;
        }
        if ("".equals(queryString)) {
            queryString = null;
        }
        String uri = contextPath + servletPath;
        if (pathInfo != null) {
            uri += pathInfo;
        }
        // good enough for tests that don't use encoded/decoded URLs
        when(request.getScheme()).thenReturn(scheme);
        when(request.getServerName()).thenReturn(host);
        when(request.getServerPort()).thenReturn(port);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContextPath()).thenReturn(contextPath);
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getPathInfo()).thenReturn(pathInfo);
        when(request.getQueryString()).thenReturn(queryString);
    }

    protected void checkEvents(String... expectedEventNames) {
        if (expectedEventNames.length == 0) {
            verifyZeroInteractions(eventProducer);
        } else {
            verify(eventProducer).fireEvent(eventCaptor.capture());
            List<Event> events = eventCaptor.getAllValues();
            List<String> eventNames = events.stream().map(Event::getName).collect(toList());
            assertEquals(Arrays.asList(expectedEventNames), eventNames);
        }
    }

    protected void checkNoEvents() {
        checkEvents(new String[] {});
    }

    protected void checkCachedUser(Map<String, Object> sessionAttributes, String username) {
        CachableUserIdentificationInfo cuii = (CachableUserIdentificationInfo) sessionAttributes.get(
                NXAuthConstants.USERIDENT_KEY);
        assertNotNull(cuii);
        assertEquals(username, cuii.getUserInfo().getUserName());
    }

    protected void checkNoCachedUser(Map<String, Object> sessionAttributes) {
        CachableUserIdentificationInfo cuii = (CachableUserIdentificationInfo) sessionAttributes.get(
                NXAuthConstants.USERIDENT_KEY);
        assertNull(cuii);
    }

    /**
     * Computation of the requested page based on request info.
     * <p>
     * Case of a servlet mapped with {@code <url-pattern>*.xhtml</url-pattern>}
     */
    @Test
    public void testRequestedPageMatchExtension() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/foo/bar.xhtml", "", "");
        // the URI passed is not used directly to do the check
        when(request.getRequestURI()).thenReturn("http://localhost:8080/nuxeo/gee/../foo/bar.xhtml;jsessionid=123");

        String page = NuxeoAuthenticationFilter.getRequestedPage(request);
        assertEquals("foo/bar.xhtml", page);
    }

    /**
     * Computation of the requested page based on request info.
     * <p>
     * Case of a servlet mapped with {@code <url-pattern>/foo/*</url-pattern>}
     */
    @Test
    public void testRequestedPageMatchPath() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/foo", "/bar.xhtml", "");
        // the URI passed is not used directly to do the check
        when(request.getRequestURI()).thenReturn("http://localhost:8080/nuxeo/gee/../foo/bar.xhtml;jsessionid=123");

        String page = NuxeoAuthenticationFilter.getRequestedPage(request);
        assertEquals("foo/bar.xhtml", page);
    }

    /**
     * Auth in session.
     */
    @Test
    public void testAuthCached() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/foo/bar", "", "");
        // cached identity
        CachableUserIdentificationInfo cuii = new CachableUserIdentificationInfo("bob", "bobpw");
        UserPrincipal principal = new UserPrincipal("bob", null, false, false);
        cuii.setPrincipal(principal);
        sessionAttributes.put(NXAuthConstants.USERIDENT_KEY, cuii);

        filter.doFilter(request, response, chain);

        // chain called as bob
        assertTrue(chain.called);
        assertEquals("bob", chain.principal.getName());
        assertSame(principal, chain.principal);

        // bob auth still cached in session
        checkCachedUser(sessionAttributes, "bob");

        // no login event
        checkNoEvents();
    }

    /**
     * No auth chain configured: no auth.
     */
    @Test
    public void testNoAuthPlugins() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getSession(eq(false))).thenReturn(null);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/foo/bar", "", "");

        filter.doFilter(request, response, chain);

        // chain called, no auth
        assertTrue(chain.called);
        assertNull(chain.principal);
    }

    /**
     * Basic immediate login. Resulting auth saved in session.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-token.xml")
    public void testAuthPluginToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/foo/bar", "", "");
        // token info
        when(request.getParameter(eq(DUMMY_AUTH_TOKEN_KEY))).thenReturn("bob");

        filter.doFilter(request, response, chain);

        // chain called as bob
        assertTrue(chain.called);
        assertEquals("bob", chain.principal.getName());

        // login success event
        checkEvents(EVENT_LOGIN_SUCCESS);

        // bob auth cached in session
        checkCachedUser(sessionAttributes, "bob");
    }

    /**
     * Basic immediate login. Resulting auth saved in session. Then redirects to previously requested page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-token.xml")
    public void testAuthPluginTokenThenRedirectToPage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/foo/bar", "", "");
        // token info + redirect page
        when(request.getParameter(eq(DUMMY_AUTH_TOKEN_KEY))).thenReturn("bob");
        when(request.getParameter(eq(REQUESTED_URL))).thenReturn("my/page");

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect
        assertFalse(chain.called);

        // login success event
        checkEvents(EVENT_LOGIN_SUCCESS);

        // bob auth cached in session
        checkCachedUser(sessionAttributes, "bob");

        // redirect was called
        verify(response).sendRedirect(eq("http://localhost:8080/nuxeo/my/page"));
    }

    /**
     * Token auth failing on specific URL not handling prompt. Redirects to hard-coded /login page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-token.xml")
    public void testAuthPluginTokenFailedSoRedirectToLoginPage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        // request specific page configured with specific chain without prompt
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/no/prompt", "", "");
        // no token provided
        // record output
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @SuppressWarnings("resource")
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, UTF_8), true);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect
        assertFalse(chain.called);

        // no login event
        checkNoEvents();

        // redirecting to /login
        verify(response).setStatus(SC_UNAUTHORIZED);
        verify(response).addHeader(eq("Location"), eq("http://localhost:8080/nuxeo/" + LOGIN_PAGE));
    }

    /**
     * No auth but redirect to plugin login page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-form.xml")
    public void testAuthPluginFormRedirectToLoginPage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        // mystart/ is defined as a start url in the XML config
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/mystart/foo", "", "");
        // record output
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @SuppressWarnings("resource")
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, UTF_8), true);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect instead
        assertFalse(chain.called);

        // no auth
        checkNoCachedUser(sessionAttributes);

        // no login event
        checkNoEvents();

        // unauthorized
        verify(response).setStatus(eq(SC_UNAUTHORIZED));
        // a redirect is done through an HTML page containing JavaScript code
        verify(response).setContentType(eq("text/html;charset=UTF-8"));
        // check that the redirect is to our dummy login page (defined in the auth plugin)
        writer.flush();
        String entity = out.toString(UTF_8);
        assertTrue(entity, entity.contains(
                "window.location = 'http://localhost:8080/nuxeo/dummy_login.jsp?requestedUrl=mystart/foo';"));
    }

    /**
     * Auth in session and request to hard-coded /login, redirects to plugin login page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-form.xml")
    public void testAuthPluginFormReLogin() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/" + LOGIN_PAGE, "", "");
        // cached identity
        CachableUserIdentificationInfo cuii = new CachableUserIdentificationInfo("bob", "bobpw");
        UserPrincipal principal = new UserPrincipal("bob", null, false, false);
        cuii.setPrincipal(principal);
        sessionAttributes.put(NXAuthConstants.USERIDENT_KEY, cuii);
        // record output
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @SuppressWarnings("resource")
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, UTF_8), true);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect instead
        assertFalse(chain.called);

        // cached auth has been removed
        // TODO checkNoCachedUser(sessionAttributes);

        // unauthorized
        verify(response).setStatus(eq(SC_UNAUTHORIZED));
        // a redirect is done through an HTML page containing JavaScript code
        verify(response).setContentType(eq("text/html;charset=UTF-8"));
        // check that the redirect is to our dummy login page (defined in the auth plugin)
        writer.flush();
        String entity = out.toString(UTF_8);
        assertTrue(entity, entity.contains("window.location = 'http://localhost:8080/nuxeo/dummy_login.jsp';"));
    }

    /**
     * Login from form auth. Resulting auth saved in session, redirects to requested page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-form.xml")
    public void testAuthPluginForm() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/doesnotmatter", "", "requestedUrl=mystart/foo");
        // login info
        when(request.getParameter(eq(DUMMY_AUTH_FORM_USERNAME_KEY))).thenReturn("bob");
        when(request.getParameter(eq(DUMMY_AUTH_FORM_PASSWORD_KEY))).thenReturn("bob");
        when(request.getParameter(eq(REQUESTED_URL))).thenReturn("mystart/foo");

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect
        assertFalse(chain.called);

        // login success event
        checkEvents(EVENT_LOGIN_SUCCESS);

        // bob auth cached in session
        checkCachedUser(sessionAttributes, "bob");

        // redirect was called
        verify(response).sendRedirect(eq("http://localhost:8080/nuxeo/mystart/foo"));
    }

    /**
     * Auth in session and /logout request. Removes session auth. Redirects to startup page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-form.xml")
    public void testAuthPluginFormLogout() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/" + LOGOUT_PAGE, "", "");
        // cached identity
        CachableUserIdentificationInfo cuii = new CachableUserIdentificationInfo("bob", "bobpw");
        cuii.getUserInfo().setAuthPluginName("DUMMY_AUTH_FORM");
        UserPrincipal principal = new UserPrincipal("bob", null, false, false);
        cuii.setPrincipal(principal);
        LoginContext loginContext = mock(LoginContext.class);
        doNothing().when(loginContext).logout();
        cuii.setLoginContext(loginContext);
        sessionAttributes.put(NXAuthConstants.USERIDENT_KEY, cuii);

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect instead
        assertFalse(chain.called);

        // logout event
        checkEvents(EVENT_LOGOUT);

        // cached auth has been removed
        checkNoCachedUser(sessionAttributes);

        // redirect was called. home.html is the default LoginScreenHelper startup page
        verify(response).sendRedirect(eq("http://localhost:8080/nuxeo/home.html"));
    }

    /**
     * No auth, no ticket, redirects to SSO login page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-sso.xml")
    public void testAuthPluginSSORedirectToSSOLoginPage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        // mystart/ is defined as a start url in the XML config
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/mystart/foo", "", "bar=baz");
        // no ticket provided
        // record output
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @SuppressWarnings("resource")
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, UTF_8), true);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect
        assertFalse(chain.called);

        // no login event
        checkNoEvents();

        // unauthorized
        verify(response).setStatus(eq(SC_UNAUTHORIZED));
        // a redirect is done through an HTML page containing JavaScript code
        verify(response).setContentType(eq("text/html;charset=UTF-8"));
        // check that the redirect is to the SSO login page
        writer.flush();
        String entity = out.toString(UTF_8);
        String expectedRedirect = URLEncoder.encode("http://localhost:8080//nuxeo/mystart/foo?bar=baz", "UTF-8");
        assertTrue(entity,
                entity.contains("window.location = 'http://sso.example.com/login?redirect=" + expectedRedirect + "';"));
    }

    /**
     * SSO redirects to page, passing a proper ticket. Resulting auth saved in session.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-sso.xml")
    public void testAuthPluginSSOWithTicket() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        // mystart/ is defined as a start url in the XML config
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/mystart/foo", "", "ticket=bob");
        // ticket info
        when(request.getParameter(eq(DUMMY_SSO_TICKET))).thenReturn("bob");
        // record output
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @SuppressWarnings("resource")
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, UTF_8), true);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        // chain called as bob
        assertTrue(chain.called);
        assertEquals("bob", chain.principal.getName());

        // login success event
        checkEvents(EVENT_LOGIN_SUCCESS);

        // bob auth cached in session
        checkCachedUser(sessionAttributes, "bob");
    }

    /**
     * Auth in session and /logout request. Removes session auth. Redirects to SSO logout page.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-loginmodule.xml")
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-authchain-dummy-sso.xml")
    public void testAuthPluginSSOLogout() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = mockSessionAttributes(session);
        when(request.getSession(anyBoolean())).thenReturn(session);
        mockRequestURI(request, "http", "localhost", 8080, "/nuxeo", "/" + LOGOUT_PAGE, "", "");
        // cached identity
        CachableUserIdentificationInfo cuii = new CachableUserIdentificationInfo("bob", "bobpw");
        cuii.getUserInfo().setAuthPluginName("DUMMY_AUTH_SSO");
        UserPrincipal principal = new UserPrincipal("bob", null, false, false);
        cuii.setPrincipal(principal);
        LoginContext loginContext = mock(LoginContext.class);
        doNothing().when(loginContext).logout();
        cuii.setLoginContext(loginContext);
        sessionAttributes.put(NXAuthConstants.USERIDENT_KEY, cuii);

        filter.doFilter(request, response, chain);

        // chain not called, as we redirect instead
        assertFalse(chain.called);

        // logout event
        checkEvents(EVENT_LOGOUT);

        // cached auth has been removed
        checkNoCachedUser(sessionAttributes);

        // redirect to the SSO logout page
        verify(response).sendRedirect(eq("http://sso.example.com/logout"));
    }

}
