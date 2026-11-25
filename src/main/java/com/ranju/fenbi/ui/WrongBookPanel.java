package com.ranju.fenbi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.ranju.fenbi.book.WrongBook;
import com.ranju.fenbi.book.WrongBook.Entry;
import com.ranju.fenbi.settings.FenbiSettings;
import com.ranju.fenbi.stats.Stats;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 「错题本」页：按 模块 → 考点 → 题目 列出错题，选中后重做（本地判分，立即出解析）。 */
final class WrongBookPanel extends JPanel implements Disposable {
    private static final String CARD_TREE = "tree";
    private static final String CARD_SHEET = "sheet";

    private record Group(String label, List<Entry> entries) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Project project;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    private final JBLabel status = new JBLabel();
    private final WebView view;
    private final Map<Long, Entry> round = new ConcurrentHashMap<>();
    private final AtomicInteger roundDone = new AtomicInteger();
    private final AtomicInteger roundRight = new AtomicInteger();

    private boolean onlyPending = true;
    private boolean showingSheet;
    private String treeStatus = "";

    WrongBookPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.view = WebView.create(this, this::onMessage);
        initTree();

        DefaultActionGroup g = new DefaultActionGroup();
        g.add(new SimpleAct("返回列表", AllIcons.Actions.Back, this::showTree, () -> showingSheet));
        g.add(new SimpleAct("重做选中的错题（不选 = 全部未掌握）", AllIcons.Actions.Execute, this::startReview,
                () -> !showingSheet));
        g.add(new PendingOnlyAction());
        g.add(new SimpleAct("刷新", AllIcons.Actions.Refresh, this::rebuildTree, () -> true));
        g.addSeparator();
        g.add(new SimpleAct("移出错题本", AllIcons.General.Remove, this::removeSelected,
                () -> !showingSheet && tree.getSelectionCount() > 0));
        g.add(new SimpleAct("清理已掌握的题", AllIcons.Actions.Checked, this::removeMastered, () -> !showingSheet));
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("FenbiWrongBook", g, true);
        toolbar.setTargetComponent(this);
        add(toolbar.getComponent(), BorderLayout.NORTH);

        center.add(new JBScrollPane(tree), CARD_TREE);
        center.add(view != null ? view.component() : WebView.unsupportedPanel(), CARD_SHEET);
        add(center, BorderLayout.CENTER);

        status.setBorder(JBUI.Borders.empty(3, 8));
        status.setForeground(UIUtil.getContextHelpForeground());
        add(status, BorderLayout.SOUTH);

        WrongBook.getInstance().changes.add(this, this::rebuildTree);
        rebuildTree();
    }

    private void initTree() {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.getEmptyText().setText("正在读取错题本…");
        TreeUIHelper.getInstance().installTreeSpeedSearch(tree);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return false;
                tree.setSelectionPath(path);
                startReview();
                return true;
            }
        }.installOn(tree);
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    startReview();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    removeSelected();
                    e.consume();
                }
            }
        });
    }

    // ---------------------------------------------------------------- 列表

    private void rebuildTree() {
        Ui.background(() -> {
            List<Entry> all = WrongBook.getInstance().all();
            Ui.edt(project, () -> buildTree(all));
        }, msg -> Ui.edt(project, () -> Ui.notify(project, "读取错题本失败：" + msg, NotificationType.WARNING)));
    }

    private void buildTree(List<Entry> all) {
        List<Entry> list = all.stream().filter(e -> !onlyPending || !e.mastered).toList();
        Comparator<String> byModule = Comparator.<String>comparingInt(Stats::orderOf).thenComparing(Comparator.naturalOrder());
        Map<String, Map<String, List<Entry>>> groups = new TreeMap<>(byModule);
        for (Entry e : list) {
            groups.computeIfAbsent(nz(e.module), k -> new LinkedHashMap<>())
                    .computeIfAbsent(nz(e.keypoint), k -> new ArrayList<>())
                    .add(e);
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (Map.Entry<String, Map<String, List<Entry>>> m : groups.entrySet()) {
            List<Entry> moduleEntries = new ArrayList<>();
            DefaultMutableTreeNode mn = new DefaultMutableTreeNode();
            for (Map.Entry<String, List<Entry>> k : m.getValue().entrySet()) {
                List<Entry> entries = new ArrayList<>(k.getValue());
                entries.sort(Comparator.comparingInt((Entry e) -> -e.wrongCount).thenComparingLong(e -> -e.lastWrongAt));
                moduleEntries.addAll(entries);
                DefaultMutableTreeNode kn = new DefaultMutableTreeNode(new Group(k.getKey(), entries));
                for (Entry e : entries) kn.add(new DefaultMutableTreeNode(e));
                mn.add(kn);
            }
            mn.setUserObject(new Group(m.getKey(), moduleEntries));
            root.add(mn);
        }
        tree.setModel(new DefaultTreeModel(root));
        TreeUtil.expand(tree, 1);

        long mastered = all.stream().filter(e -> e.mastered).count();
        if (list.isEmpty()) {
            tree.getEmptyText().setText(all.isEmpty()
                    ? "错题本是空的：交卷后做错的题会自动收进来"
                    : "没有未掌握的错题 🎉（取消「只看未掌握」可以看全部）");
        }
        treeStatus = "共 " + all.size() + " 道 · 未掌握 " + (all.size() - mastered) + " · 已掌握 " + mastered
                + " · 双击重做 · 连续答对 " + FenbiSettings.options().masterStreak + " 次算掌握";
        updateStatus();
    }

    private List<Entry> selectedEntries() {
        Map<Long, Entry> out = new LinkedHashMap<>();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) return List.of();
        for (TreePath p : paths) {
            Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (o instanceof Group g) g.entries().forEach(e -> out.putIfAbsent(e.questionId, e));
            else if (o instanceof Entry e) out.putIfAbsent(e.questionId, e);
        }
        return new ArrayList<>(out.values());
    }

    private final class Renderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree t, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) return;
            Object o = node.getUserObject();
            if (o instanceof Group g) {
                long pending = g.entries().stream().filter(e -> !e.mastered).count();
                append(g.label());
                append("  " + pending + " 道未掌握" + (pending == g.entries().size() ? "" : " / 共 " + g.entries().size()),
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (o instanceof Entry e) {
                String text = e.plainText();
                append(text.length() > 30 ? text.substring(0, 30) + "…" : text);
                append("  错 " + e.wrongCount + " 次", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                if (e.mastered) {
                    append("  已掌握", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN));
                } else if (e.streak > 0) {
                    append("  已连对 " + e.streak, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 重做

    private void startReview() {
        List<Entry> list = selectedEntries();
        if (list.isEmpty()) {
            list = WrongBook.getInstance().all().stream().filter(e -> !e.mastered).toList();
        }
        if (list.isEmpty()) {
            Messages.showInfoMessage(project, "没有可以重做的错题。", "错题本");
            return;
        }
        List<Entry> sorted = new ArrayList<>(list);
        // 未掌握优先，错得多的优先，最久没复习的优先
        sorted.sort(Comparator.comparing((Entry e) -> e.mastered)
                .thenComparingInt(e -> -e.wrongCount)
                .thenComparingLong(e -> e.lastReviewAt));
        int limit = FenbiSettings.options().questionCount > 0 ? FenbiSettings.options().questionCount : 20;
        if (sorted.size() > limit) sorted = new ArrayList<>(sorted.subList(0, limit));

        round.clear();
        sorted.forEach(e -> round.put(e.questionId, e));
        roundDone.set(0);
        roundRight.set(0);
        if (view != null) {
            view.load(SheetRenderer.render(Sheet.review(sorted), view.bridge(), SheetStyle.current()));
        }
        showingSheet = true;
        cards.show(center, CARD_SHEET);
        updateStatus();
        if (view != null) view.focus();
    }

    /** 页面回调（CEF 线程）：rev|题目id|选项 */
    private void onMessage(String msg) {
        if (msg == null || !msg.startsWith("rev|")) return;
        String[] p = msg.split("\\|");
        if (p.length < 3) return;
        final long id;
        try {
            id = Long.parseLong(p[1]);
        } catch (NumberFormatException e) {
            return;
        }
        if (!round.containsKey(id)) return;
        String choice = p[2];
        Ui.background(() -> {
            boolean ok = WrongBook.getInstance().recordReview(id, choice);
            roundDone.incrementAndGet();
            if (ok) roundRight.incrementAndGet();
            Ui.edt(project, this::updateStatus);
        }, err -> Ui.edt(project, () -> Ui.notify(project, "保存错题记录失败：" + err, NotificationType.WARNING)));
    }

    private void removeSelected() {
        List<Entry> list = selectedEntries();
        if (list.isEmpty()) return;
        if (Messages.showYesNoDialog(project, "从错题本移除这 " + list.size() + " 道题？", "错题本", null) != Messages.YES) return;
        List<Long> ids = list.stream().map(e -> e.questionId).toList();
        Ui.background(() -> WrongBook.getInstance().remove(ids),
                err -> Ui.edt(project, () -> Ui.notify(project, "移除失败：" + err, NotificationType.WARNING)));
    }

    private void removeMastered() {
        if (Messages.showYesNoDialog(project, "把所有「已掌握」的题移出错题本？", "错题本", null) != Messages.YES) return;
        Ui.background(() -> {
            int n = WrongBook.getInstance().removeMastered();
            Ui.edt(project, () -> Ui.notify(project, "已清理 " + n + " 道已掌握的题", NotificationType.INFORMATION));
        }, err -> Ui.edt(project, () -> Ui.notify(project, "清理失败：" + err, NotificationType.WARNING)));
    }

    // ---------------------------------------------------------------- 视图

    private void showTree() {
        showingSheet = false;
        cards.show(center, CARD_TREE);
        updateStatus();
    }

    private void updateStatus() {
        if (showingSheet) {
            status.setText("错题重做 · 已做 " + roundDone.get() + "/" + round.size() + " · 正确 " + roundRight.get());
        } else {
            status.setText(treeStatus);
        }
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "未分类" : s;
    }

    @Override
    public void dispose() {
        round.clear();
    }

    private final class PendingOnlyAction extends ToggleAction implements DumbAware {
        PendingOnlyAction() {
            super("只看未掌握", "只看未掌握的错题", AllIcons.General.Filter);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return onlyPending;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            onlyPending = state;
            rebuildTree();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
