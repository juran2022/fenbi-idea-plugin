package com.ranju.fenbi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.ranju.fenbi.log.PracticeLog;
import com.ranju.fenbi.stats.Stats;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/** 「统计」页：读练习记录，画各模块的时长、正确率和相关性。 */
final class StatsPanel extends JPanel implements Disposable {
    private final Project project;
    private final WebView view;
    private volatile int rangeDays = 0;

    StatsPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.view = WebView.create(this, this::onMessage);

        DefaultActionGroup g = new DefaultActionGroup();
        g.add(new SimpleAct("刷新", AllIcons.Actions.Refresh, this::refresh, () -> true));
        g.add(new SimpleAct("打开练习记录（CSV）", AllIcons.Actions.MenuOpen, () -> Ui.openLog(project), () -> true));
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("FenbiStats", g, true);
        toolbar.setTargetComponent(this);
        add(toolbar.getComponent(), BorderLayout.NORTH);
        add(view != null ? view.component() : WebView.unsupportedPanel(), BorderLayout.CENTER);

        PracticeLog.CHANGES.add(this, this::refresh);
        refresh();
    }

    void refresh() {
        if (view == null) return;
        SheetStyle style = SheetStyle.current();
        String bridge = view.bridge();
        int range = rangeDays;
        Ui.background(() -> {
            Stats st = Stats.compute(PracticeLog.read(), range);
            String html = StatsRenderer.render(st, range, bridge, style);
            Ui.edt(project, () -> view.load(html));
        }, msg -> Ui.edt(project, () -> Ui.notify(project, "统计失败：" + msg, NotificationType.WARNING)));
    }

    /** 页面回调：range|天数 */
    private void onMessage(String msg) {
        if (msg == null || !msg.startsWith("range|")) return;
        try {
            rangeDays = Integer.parseInt(msg.substring("range|".length()));
        } catch (NumberFormatException e) {
            return;
        }
        Ui.edt(project, this::refresh);
    }

    @Override
    public void dispose() {
        // WebView 由 Disposer 释放
    }
}
