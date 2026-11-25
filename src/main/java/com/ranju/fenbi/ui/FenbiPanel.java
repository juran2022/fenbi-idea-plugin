package com.ranju.fenbi.ui;

import com.google.gson.Gson;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.ranju.fenbi.api.FenbiClient;
import com.ranju.fenbi.api.FenbiException;
import com.ranju.fenbi.api.Models.Category;
import com.ranju.fenbi.api.Models.Exercise;
import com.ranju.fenbi.api.Models.Material;
import com.ranju.fenbi.api.Models.Question;
import com.ranju.fenbi.api.Models.QuestionSet;
import com.ranju.fenbi.api.Models.UnfinishedCache;
import com.ranju.fenbi.book.WrongBook;
import com.ranju.fenbi.log.PracticeLog;
import com.ranju.fenbi.settings.FenbiConfigurable;
import com.ranju.fenbi.settings.FenbiSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 「练习」页：上面工具栏，中间“目录树 / 卷面”两张卡片，下面状态栏。 */
final class FenbiPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(FenbiPanel.class);
    private static final String CARD_TREE = "tree";
    private static final String CARD_SHEET = "sheet";
    private static final Gson GSON = new Gson();

    private final Project project;
    private final FenbiClient client = new FenbiClient("xingce");
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    private final JBLabel status = new JBLabel();
    private final WebView view;
    private final Timer timer;
    /** IDE 失去焦点后稍等一下再确认，避免弹窗、通知等瞬间抢焦点误触发暂停 */
    private final Timer deactivateCheck;

    private volatile Session session;
    private volatile UnfinishedCache cache;
    private boolean showingSheet;
    private String statusMessage = "";

    FenbiPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.view = WebView.create(this, this::onMessage);
        initTree();

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("FenbiToolbar", createActions(), true);
        toolbar.setTargetComponent(this);
        add(toolbar.getComponent(), BorderLayout.NORTH);

        center.add(new JBScrollPane(tree), CARD_TREE);
        center.add(view != null ? view.component() : WebView.unsupportedPanel(), CARD_SHEET);
        add(center, BorderLayout.CENTER);

        status.setBorder(JBUI.Borders.empty(3, 8));
        status.setForeground(UIUtil.getContextHelpForeground());
        add(status, BorderLayout.SOUTH);

        timer = new Timer(1000, e -> tick());
        timer.start();

        // ---- 切屏自动暂停：① 切到其他程序 ② 刷题窗口被隐藏 / 切到其他标签页
        deactivateCheck = new Timer(600, e -> {
            if (!ApplicationManager.getApplication().isActive()) autoPause("切到了其他程序");
        });
        deactivateCheck.setRepeats(false);
        MessageBusConnection bus = ApplicationManager.getApplication().getMessageBus().connect(this);
        bus.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
            @Override
            public void applicationActivated(@NotNull IdeFrame ideFrame) {
                deactivateCheck.stop();
                maybeAutoResume();
            }

            @Override
            public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
                deactivateCheck.restart();
            }
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (isShowing()) maybeAutoResume();
            else autoPause("离开了刷题页面");
        });

        loadCategories();
    }

    // ---------------------------------------------------------------- 初始化

    private void initTree() {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new CategoryRenderer());
        tree.getEmptyText().setText("正在加载目录…");
        TreeUIHelper.getInstance().installTreeSpeedSearch(tree);

        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null || !((DefaultMutableTreeNode) path.getLastPathComponent()).isLeaf()) return false;
                startSelected();
                return true;
            }
        }.installOn(tree);

        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    startSelected();
                    e.consume();
                }
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }

            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                tree.setSelectionPath(path);
                JPopupMenu menu = new JPopupMenu();
                JMenuItem item = new JMenuItem("开始练习（" + path.getLastPathComponent() + "）");
                item.addActionListener(a -> startSelected());
                menu.add(item);
                menu.show(tree, e.getX(), e.getY());
            }
        });
    }

    private DefaultActionGroup createActions() {
        DefaultActionGroup g = new DefaultActionGroup();
        g.add(new SimpleAct("返回目录", AllIcons.Actions.Back, this::showTree, () -> showingSheet));
        g.add(new SimpleAct("回到卷面", AllIcons.Actions.Forward, this::showSheet,
                () -> !showingSheet && session != null));
        g.add(new SimpleAct("刷新目录", AllIcons.Actions.Refresh, this::loadCategories, () -> true));
        g.add(new SimpleAct("交卷（Ctrl+Enter）", AllIcons.Actions.Commit, this::submit,
                () -> session != null && !session.submitted && !session.submitting));
        g.add(new PauseAction());

        DefaultActionGroup timing = DefaultActionGroup.createPopupGroup(() -> "计时模式");
        timing.getTemplatePresentation().setIcon(AllIcons.Vcs.History);
        timing.add(new LimitModeAction("不限时", FenbiSettings.LIMIT_NONE));
        timing.add(new LimitModeAction("整卷限时", FenbiSettings.LIMIT_PAPER));
        timing.add(new LimitModeAction("每题限时", FenbiSettings.LIMIT_QUESTION));
        timing.addSeparator();
        timing.add(new AutoSubmitAction());
        timing.add(new OptionToggle("切屏自动暂停", () -> FenbiSettings.options().autoPause,
                v -> FenbiSettings.options().autoPause = v));
        timing.add(new OptionToggle("切回来自动继续", () -> FenbiSettings.options().autoResume,
                v -> FenbiSettings.options().autoResume = v));
        timing.add(new OptionToggle("暂停时隐藏暂停提示", () -> FenbiSettings.options().hidePauseInfo, v -> {
            FenbiSettings.options().hidePauseInfo = v;
            Session s = session;
            if (s != null) syncPause(s); // 正在暂停时立即生效
            updateStatus();
        }));
        timing.add(new SimpleAct("时长设置…", null, this::openSettings, () -> true));
        g.add(timing);

        g.add(new SimpleAct("在粉笔网页查看这套题", AllIcons.General.Web, this::openWeb, () -> session != null));
        g.addSeparator();
        g.add(new SimpleAct("打开练习记录（CSV）", AllIcons.Actions.MenuOpen, () -> Ui.openLog(project), () -> true));
        g.add(new SimpleAct("设置", AllIcons.General.Settings, this::openSettings, () -> true));
        return g;
    }

    private void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, FenbiConfigurable.class);
    }

    // ---------------------------------------------------------------- 目录

    private void loadCategories() {
        setStatus("正在加载目录…");
        runInBackground("加载目录失败", () -> {
            List<Category> categories = client.categories();
            UnfinishedCache unfinished = null;
            try {
                unfinished = client.unfinished();
            } catch (FenbiException e) {
                LOG.info("获取未完成练习失败：" + e.getMessage());
            }
            UnfinishedCache u = unfinished;
            Ui.edt(project, () -> {
                cache = u;
                DefaultMutableTreeNode root = new DefaultMutableTreeNode();
                for (Category c : categories) root.add(toNode(c));
                tree.setModel(new DefaultTreeModel(root));
                tree.getEmptyText().setText("目录为空");
                setStatus("双击考点开始练习 · 当前" + FenbiSettings.limitDescription() + " · Ctrl+Alt+Shift+F 隐藏窗口");
            });
        });
    }

    private static DefaultMutableTreeNode toNode(Category c) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(c);
        if (c.children != null) for (Category child : c.children) node.add(toNode(child));
        return node;
    }

    private boolean isUnfinished(Category c) {
        UnfinishedCache u = cache;
        return u != null && u.exerciseId > 0 && u.keypointIds != null && u.keypointIds.contains(c.id);
    }

    private final class CategoryRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree t, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node) || !(node.getUserObject() instanceof Category c)) return;
            append(c.name == null ? "" : c.name);
            append("  " + c.answerCount + "/" + c.count, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            if (isUnfinished(c)) {
                append("  未完成", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.ORANGE));
            }
        }
    }

    // ---------------------------------------------------------------- 作答

    private void startSelected() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof Category c)) return;
        Object top = ((DefaultMutableTreeNode) path.getPathComponent(Math.min(1, path.getPathCount() - 1))).getUserObject();
        String module = top instanceof Category m ? m.name : c.name;
        start(c, module);
    }

    private void start(Category c, String module) {
        Session old = session;
        if (old != null && !old.submitted && !old.submitting && !old.answers.isEmpty()) {
            int r = Messages.showYesNoDialog(project, "上一套题还没交卷（进度会保存在粉笔，可以之后在目录里继续）。确定开始新的一套吗？",
                    "开始新练习", null);
            if (r != Messages.YES) return;
        }
        if (view == null) {
            showSheet();
            return;
        }
        setStatus("正在组卷：" + c.name + " …");
        boolean resume = isUnfinished(c);
        long cachedId = resume ? cache.exerciseId : 0;
        FenbiSettings.Options o = FenbiSettings.options();
        int limit = o.questionCount;
        runInBackground("组卷失败", () -> {
            long exerciseId = resume ? cachedId : client.createExercise(c.id, limit);
            Exercise ex = client.exercise(exerciseId);
            QuestionSet qs = client.questions(exerciseId);
            if (qs.items().isEmpty()) throw new FenbiException("这个考点没有拿到题目");
            Session s = new Session(exerciseId, module, c.name, qs, ex);
            if (FenbiSettings.LIMIT_PAPER.equals(o.limitMode)) {
                int minutes = o.paperMinutes > 0 ? o.paperMinutes
                        : (int) Math.ceil(s.questions.size() * o.questionSeconds / 60.0);
                s.deadline = s.startedAt + Math.max(1, minutes) * 60_000L;
            } else if (FenbiSettings.LIMIT_QUESTION.equals(o.limitMode)) {
                s.questionSeconds = Math.max(5, o.questionSeconds);
            }
            Ui.edt(project, () -> {
                session = s;
                view.load(SheetRenderer.render(Sheet.answer(s), view.bridge(), SheetStyle.current()));
                showSheet();
                view.focus();
            });
        });
    }

    /** 页面回调（在 CEF 线程上）。格式见 sheet.js 顶部注释。 */
    private void onMessage(String msg) {
        Session s = session;
        if (s == null || msg == null) return;
        String[] p = msg.split("\\|");
        switch (p[0]) {
            case "ans" -> onAnswer(s, p);
            case "timeout" -> {
                if (p.length > 1) {
                    try {
                        s.timeouts.add(Long.parseLong(p[1]));
                    } catch (NumberFormatException ignored) {
                        // 忽略
                    }
                }
            }
            case "pause" -> Ui.edt(project, () -> {
                if (session == s) togglePause();
            });
            case "resume" -> Ui.edt(project, () -> {
                if (session == s) resume(s);
            });
            case "q" -> {
                // 每题限时：当前题状态。q|left|剩余秒数 / q|done / q|timeout
                String status = switch (p.length > 1 ? p[1] : "") {
                    case "left" -> {
                        try {
                            yield "本题剩余 " + Ui.mmss(Long.parseLong(p[2]));
                        } catch (RuntimeException e) {
                            yield null;
                        }
                    }
                    case "done" -> "本题已答";
                    case "timeout" -> "本题已超时";
                    default -> null;
                };
                if (status != null) {
                    s.questionStatus = status;
                    Ui.edt(project, this::updateStatus);
                }
            }
            case "ready" -> Ui.edt(project, () -> {
                // 页面加载完成：如果加载期间已经被暂停（比如刚组完卷就切走了），把状态同步过去
                if (session == s && s.isPaused()) syncPause(s);
            });
            case "submit" -> {
                boolean auto = p.length > 1 && "auto".equals(p[1]);
                Ui.edt(project, () -> {
                    if (session != s) return;
                    if (auto) doSubmit(s, true);
                    else submit();
                });
            }
            default -> LOG.info("未知的页面消息：" + msg);
        }
    }

    private void onAnswer(Session s, String[] p) {
        if (s.submitted || s.submitting || p.length < 5) return;
        try {
            long questionId = Long.parseLong(p[1]);
            int index = Integer.parseInt(p[2]);
            String choice = p[3];
            int seconds = Integer.parseInt(p[4]);
            s.answers.put(questionId, choice);
            s.spent.put(questionId, seconds);
            runInBackground("保存答案失败", () -> client.answer(s.exerciseId, questionId, index, choice, seconds));
            Ui.edt(project, this::updateStatus);
        } catch (NumberFormatException e) {
            LOG.warn("无法解析作答回调：" + String.join("|", p));
        }
    }

    private void submit() {
        Session s = session;
        if (s == null || s.submitted || s.submitting) return;
        int unanswered = s.questions.size() - s.answers.size();
        String msg = unanswered > 0 ? "还有 " + unanswered + " 题没做，确定交卷吗？" : "确定交卷吗？";
        if (Messages.showYesNoDialog(project, msg, "交卷", null) != Messages.YES) return;
        doSubmit(s, false);
    }

    /** 交卷 → 拉解析 → 记错题本 → 记练习记录 → 显示解析。auto = 超时自动交卷。 */
    private void doSubmit(Session s, boolean auto) {
        if (s.submitted || s.submitting) return;
        s.submitting = true;
        setStatus(auto ? "时间到，正在自动交卷…" : "正在交卷…");
        runInBackground("交卷失败", () -> {
            try {
                client.submit(s.exerciseId);
                QuestionSet sol = client.solutions(s.exerciseId);
                List<Question> items = sol.items().isEmpty() ? s.questions : sol.items();
                List<Material> mats = sol.materialList().isEmpty() ? s.materials : sol.materialList();
                s.markSubmitted(items);
                int wrong = WrongBook.getInstance().recordExercise(s.module, s.keypoint, items, mats, s.answers);
                PracticeLog.append(s.module, s.keypoint, s.questions.size(), s.answers.size(), s.correct,
                        s.elapsedSeconds());
                Ui.edt(project, () -> {
                    if (session == s && view != null) {
                        view.load(SheetRenderer.render(Sheet.solution(s, sol), view.bridge(), SheetStyle.current()));
                        showSheet();
                        view.focus();
                    }
                    Ui.notify(project, (auto ? "时间到，已自动交卷：" : "已交卷：") + "正确 " + s.correct + "/"
                            + items.size() + (wrong > 0 ? "，" + wrong + " 道题已记入错题本" : "，全对！"),
                            NotificationType.INFORMATION);
                    loadCategories(); // 刷新做题数和“未完成”标记
                });
            } finally {
                s.submitting = false;
            }
        });
    }

    /** 每秒一次：整卷限时检查 + 刷新状态栏。 */
    private void tick() {
        Session s = session;
        if (s != null && s.deadline > 0 && !s.submitted && !s.submitting && !s.isPaused()) {
            long left = s.deadline - s.activeNow();
            long total = s.deadline - s.startedAt;
            if (!s.warned && total > 180_000 && left > 0 && left <= 60_000) {
                s.warned = true;
                Ui.notify(project, "还剩 1 分钟", NotificationType.INFORMATION);
            }
            if (left <= 0) {
                if (FenbiSettings.options().autoSubmit) {
                    doSubmit(s, true);
                } else if (!s.timeUpNotified) {
                    s.timeUpNotified = true;
                    Ui.notify(project, "时间到！（没开自动交卷，可以做完再交，超时会记在用时里）", NotificationType.WARNING);
                }
            }
        }
        updateStatus();
    }

    private void openWeb() {
        Session s = session;
        if (s != null) BrowserUtil.browse(FenbiClient.webSolutionUrl(s.exerciseId));
    }

    // ---------------------------------------------------------------- 视图 & 状态

    private void showTree() {
        autoPause("回到了目录");
        showingSheet = false;
        cards.show(center, CARD_TREE);
        updateStatus();
    }

    private void showSheet() {
        showingSheet = true;
        cards.show(center, CARD_SHEET);
        updateStatus();
        if (view != null) view.focus();
        maybeAutoResume();
    }

    private void setStatus(String message) {
        statusMessage = message;
        updateStatus();
    }

    private void updateStatus() {
        Session s = session;
        if (!showingSheet || s == null) {
            status.setText(statusMessage);
            return;
        }
        int n = s.questions.size();
        String text;
        if (s.submitted) {
            text = "已交卷 · 正确 " + s.correct + "/" + n + " · 用时 " + Ui.mmss(s.elapsedSeconds());
        } else {
            text = (s.isPaused()
                    ? (FenbiSettings.options().hidePauseInfo ? "⏸ · " : "⏸ 已暂停（" + s.pauseReason() + "）· ")
                    : "")
                    + "已答 " + s.answers.size() + "/" + n + " · ";
            if (s.deadline > 0) {
                long left = (s.deadline - s.activeNow()) / 1000;
                text += left >= 0 ? "剩余 " + Ui.mmss(left) : "超时 +" + Ui.mmss(-left);
            } else if (s.questionSeconds > 0) {
                text += "每题 " + s.questionSeconds + " 秒 · 用时 " + Ui.mmss(s.elapsedSeconds());
                if (s.questionStatus != null) text += " · " + s.questionStatus;
            } else {
                text += "用时 " + Ui.mmss(s.elapsedSeconds());
            }
            if (s.submitting) text += " · 交卷中…";
        }
        status.setText(s.keypoint + " · " + text);
    }

    // ---------------------------------------------------------------- 暂停

    private static boolean pausable(Session s) {
        return s != null && !s.submitted && !s.submitting;
    }

    private void togglePause() {
        Session s = session;
        if (!pausable(s)) return;
        if (s.isPaused()) resume(s);
        else pause(s, "手动暂停", false);
    }

    private void pause(Session s, String reason, boolean auto) {
        if (!pausable(s) || !s.pause(reason, auto)) return;
        syncPause(s);
        updateStatus();
    }

    private void resume(Session s) {
        if (s == null || !s.resume()) return;
        syncPause(s);
        updateStatus();
        if (view != null && showingSheet) view.focus();
    }

    /** 切屏触发的暂停；设置里关掉了就什么都不做。 */
    private void autoPause(String reason) {
        if (!FenbiSettings.options().autoPause) return;
        pause(session, reason, true);
    }

    /** 切回来后：开了「自动继续」、是自动暂停、而且卷面确实在眼前，才继续计时。 */
    private void maybeAutoResume() {
        Session s = session;
        if (s == null || !s.isAutoPaused() || !FenbiSettings.options().autoResume) return;
        if (!showingSheet || !isShowing() || !ApplicationManager.getApplication().isActive()) return;
        resume(s);
    }

    /** 把 Java 这边的暂停状态（权威）同步到页面上的计时器。 */
    private void syncPause(Session s) {
        if (view == null || session != s) return;
        view.exec("window.__setPaused&&window.__setPaused(" + s.isPaused() + "," + GSON.toJson(s.pauseReason())
                + "," + s.totalPausedMs() + "," + FenbiSettings.options().hidePauseInfo + ")");
    }

    // ---------------------------------------------------------------- 线程工具

    private void runInBackground(String errorTitle, Ui.Task task) {
        Ui.background(task, msg -> Ui.edt(project, () -> showError(errorTitle + "：" + msg)));
    }

    private void showError(String message) {
        setStatus(message);
        if (message.contains("Cookie")) {
            tree.getEmptyText().setText("需要先填写粉笔 Cookie");
            tree.getEmptyText().appendSecondaryText("打开设置", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    e -> openSettings());
        }
        Ui.notify(project, message, NotificationType.WARNING);
    }

    @Override
    public void dispose() {
        timer.stop();
        deactivateCheck.stop();
    }

    // ---------------------------------------------------------------- 计时模式菜单

    private static final class LimitModeAction extends ToggleAction implements DumbAware {
        private final String mode;

        LimitModeAction(String text, String mode) {
            super(text);
            this.mode = mode;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return mode.equals(FenbiSettings.options().limitMode);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) FenbiSettings.options().limitMode = mode;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            FenbiSettings.Options o = FenbiSettings.options();
            String text = switch (mode) {
                case FenbiSettings.LIMIT_PAPER -> o.paperMinutes > 0
                        ? "整卷限时（" + o.paperMinutes + " 分钟）"
                        : "整卷限时（每题 " + o.questionSeconds + " 秒折算）";
                case FenbiSettings.LIMIT_QUESTION -> "每题限时（" + o.questionSeconds + " 秒）";
                default -> "不限时";
            };
            e.getPresentation().setText(text);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    /** 工具栏「暂停 / 继续」按钮。 */
    private final class PauseAction extends ToggleAction implements DumbAware {
        PauseAction() {
            super("暂停 / 继续计时（空格）", "暂停期间不计时，题目会被遮住", AllIcons.Actions.Pause);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            Session s = session;
            return s != null && s.isPaused();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            Session s = session;
            if (!pausable(s)) return;
            if (state) pause(s, "手动暂停", false);
            else resume(s);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            e.getPresentation().setEnabled(showingSheet && pausable(session));
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /** 计时菜单里的布尔开关。 */
    private static final class OptionToggle extends ToggleAction implements DumbAware {
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        OptionToggle(String text, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(text);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return getter.get();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            setter.accept(state);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private static final class AutoSubmitAction extends ToggleAction implements DumbAware {
        AutoSubmitAction() {
            super("时间到自动交卷");
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return FenbiSettings.options().autoSubmit;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            FenbiSettings.options().autoSubmit = state;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }
}
