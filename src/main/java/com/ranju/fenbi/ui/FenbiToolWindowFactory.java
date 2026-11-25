package com.ranju.fenbi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/** 工具窗口三个标签页：练习 / 错题本 / 统计。 */
public final class FenbiToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentManager cm = toolWindow.getContentManager();
        ContentFactory cf = ContentFactory.getInstance();
        add(cm, cf, new FenbiPanel(project), "练习");
        add(cm, cf, new WrongBookPanel(project), "错题本");
        add(cm, cf, new StatsPanel(project), "统计");
    }

    private static <T extends JComponent & Disposable> void add(ContentManager cm, ContentFactory cf, T panel,
                                                              String title) {
        Content content = cf.createContent(panel, title, false);
        content.setDisposer(panel);
        cm.addContent(content);
    }
}
