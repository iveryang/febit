/**
 * Copyright 2013 febit.org (support@febit.org)
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
 */
package org.febit.web;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.febit.App;
import org.febit.util.Petite;
import org.febit.util.PropsUtil;
import org.febit.web.component.ActionManager;

/**
 *
 * @author zqq90
 */
public class WebApp extends App implements javax.servlet.Filter {

    private static final ThreadLocal<ActionRequest> LOACL_REQ = new ThreadLocal<>();
    protected static final String BASE_WEBAPP = "classpath:febit-web-base.webapp";

    protected static WebApp _instance;

    protected ServletContext servletContext;

    //Components
    protected ActionManager actionManager;

    @Override
    protected void loadProps(String propsFiles) {
        PropsUtil.load(_props, BASE_WEBAPP);
        super.loadProps(propsFiles);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.servletContext = filterConfig.getServletContext();
        String propsFiles = filterConfig.getInitParameter("props");
        if (propsFiles == null) {
            propsFiles = "/*.webapp";
        }
        _instance = this;
        start(propsFiles);
    }

    @Override
    public void destroy() {
        stop();
        _instance = null;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain filterChain) throws IOException, ServletException {

        final ActionRequest request = this.actionManager.buildActionRequest((HttpServletRequest) req, (HttpServletResponse) res);
        if (request != null) {
            LOACL_REQ.set(request);
            try {
                request.invoke();
            } catch (Exception ex) {
                handleException(ex);
            } finally {
                LOACL_REQ.remove();
            }
        } else {
            filterChain.doFilter(req, res);
        }
    }

    protected void handleException(Exception ex) throws IOException, ServletException {
        if (ex instanceof IOException) {
            throw (IOException) ex;
        }
        if (ex instanceof ServletException) {
            throw (ServletException) ex;
        }
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
        throw new ServletException(ex);
    }

    public ServletContext getServletContext() {
        return this.servletContext;
    }

    public static ActionRequest request() {
        return LOACL_REQ.get();
    }

    public static HttpSession session() {
        ActionRequest request = request();
        if (request != null) {
            return request.request.getSession();
        }
        return null;
    }

    public static Object session(String key) {
        ActionRequest request = request();
        if (request != null) {
            return request.request.getSession().getAttribute(key);
        }
        return null;
    }

    public static ServletContext servletContext() {
        return _instance.servletContext;
    }

    public static Petite petite() {
        return _instance._petite;
    }

    public static WebApp instance() {
        return _instance;
    }

    public static void add(final Object bean) {
        _instance.addBean(bean);
    }

    public static void inject(final Object bean) {
        _instance.injectBean(bean);
    }

    public static Object get(String name) {
        return _instance.getBean(name);
    }

    public static <T> T get(Class<T> type) {
        return _instance.getBean(type);
    }

    public static <T> T component(Class<T> type) {
        return _instance.getBean(type);
    }
}