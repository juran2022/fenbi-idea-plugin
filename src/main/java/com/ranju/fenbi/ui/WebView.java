package com.ranju.fenbi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/** JCEF 浏览器 + JS→Java 回调通道。页面里调用 window.__send(字符串) 即可回传。 */
final class WebView implements Disposable {
    private static final Logger LOG = Logger.getInstance(WebView.class);

    private final JBCefBrowser browser;
    private final JBCefJSQuery query;

    private WebView(Consumer<String> handler) {
        browser = JBCefBrowser.createBuilder().build();
        // JS 查询要在浏览器真正创建之前建好；create(JBCefBrowser) 已废弃，用 JBCefBrowserBase 重载
        query = JBCefJSQuery.create((JBCefBrowserBase) browser);
        query.addHandler(request -> {
            try {
                handler.accept(request);
            } catch (RuntimeException e) {
                LOG.warn("处理页面回调失败：" + request, e);
            }
            return null;
        });
        Disposer.register(this, browser);
        Disposer.register(this, query);
    }

    /** 当前 IDE 不支持 JCEF 时返回 null。 */
    static WebView create(Disposable parent, Consumer<String> handler) {
        if (!JBCefApp.isSupported()) return null;
        WebView view = new WebView(handler);
        Disposer.register(parent, view);
        return view;
    }

    JComponent component() {
        return browser.getComponent();
    }

    /** 注入到页面里的 JS 片段，变量 p 是要回传的字符串。 */
    String bridge() {
        return query.inject("p");
    }

    void load(String html) {
        browser.loadHTML(html);
    }

    /** 在当前页面里执行一段 JS（页面还没加载完时会被忽略，所以页面加载后会主动发 ready 来同步状态）。 */
    void exec(String js) {
        CefBrowser cef = browser.getCefBrowser();
        cef.executeJavaScript(js, cef.getURL(), 0);
    }

    /** 让卷面拿到键盘焦点。 */
    void focus() {
        ApplicationManager.getApplication().invokeLater(() -> {
            browser.getComponent().requestFocusInWindow();
            browser.getCefBrowser().setFocus(true);
        });
    }

    static JComponent unsupportedPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JBLabel("当前 IDE 不支持 JCEF，无法显示题目。请在 Help → Find Action → "
                + "Choose Boot Java Runtime 里换成带 JCEF 的 JBR。", SwingConstants.CENTER), BorderLayout.CENTER);
        return p;
    }

    @Override
    public void dispose() {
        // 子对象由 Disposer 释放
    }
}
