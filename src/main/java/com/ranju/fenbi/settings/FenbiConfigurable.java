package com.ranju.fenbi.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.List;
import java.util.Objects;

public final class FenbiConfigurable implements Configurable {
    private static final List<String> LIMIT_VALUES =
            List.of(FenbiSettings.LIMIT_NONE, FenbiSettings.LIMIT_PAPER, FenbiSettings.LIMIT_QUESTION);

    private JBTextArea cookieArea;
    private JBIntSpinner fontSpinner;
    private JBIntSpinner countSpinner;
    private JBCheckBox stealthBox;
    private ComboBox<String> limitCombo;
    private JBIntSpinner paperSpinner;
    private JBIntSpinner secondsSpinner;
    private JBCheckBox autoSubmitBox;
    private JBCheckBox autoPauseBox;
    private JBCheckBox autoResumeBox;
    private JBCheckBox hidePauseInfoBox;
    private JBCheckBox autoNextBox;
    private JBCheckBox pinBox;
    private JBIntSpinner streakSpinner;
    private String savedCookie = "";

    @Override
    public @Nls String getDisplayName() {
        return "Fenbi 行测";
    }

    @Override
    public @Nullable JComponent createComponent() {
        cookieArea = new JBTextArea(6, 60);
        cookieArea.setLineWrap(true);
        fontSpinner = new JBIntSpinner(13, 9, 32);
        countSpinner = new JBIntSpinner(10, 0, 50);
        stealthBox = new JBCheckBox("低调模式（使用编辑器字体和配色，图片半透明，鼠标移上去恢复）");
        limitCombo = new ComboBox<>(new String[]{"不限时", "整卷限时", "每题限时"});
        paperSpinner = new JBIntSpinner(0, 0, 300);
        secondsSpinner = new JBIntSpinner(60, 10, 600);
        autoSubmitBox = new JBCheckBox("时间到自动交卷（每题限时：最后一题超时后自动交卷）");
        autoPauseBox = new JBCheckBox("切屏自动暂停（切到其他程序、隐藏刷题窗口、切到其他标签页或返回目录时）");
        autoResumeBox = new JBCheckBox("切回来自动继续（不勾选则需要点一下卷面或按空格继续）");
        hidePauseInfoBox = new JBCheckBox("暂停时隐藏暂停提示（卷面只留空白，点击或按空格仍可继续）");
        autoNextBox = new JBCheckBox("选完选项自动跳到下一道没做的题");
        pinBox = new JBCheckBox("有材料的题（资料分析等）默认把材料固定在卷面顶部");
        streakSpinner = new JBIntSpinner(2, 1, 10);

        JBLabel hint = new JBLabel("<html>获取方法：浏览器登录 www.fenbi.com → F12 打开开发者工具 → Network → "
                + "点开任意一个 tiku.fenbi.com 的请求 → 复制 Request Headers 里 Cookie 的整段值。<br>"
                + "Cookie 保存在 IDE 的密码库（Password Safe），不会明文写进配置文件。</html>");
        hint.setForeground(UIUtil.getContextHelpForeground());
        JBLabel limitHint = new JBLabel("<html>行测真题大约 120 分钟 130 题，平均每题 55 秒左右。<br>"
                + "工具窗口工具栏的「计时模式」按钮也可以随时切换，从下一套题开始生效。</html>");
        limitHint.setForeground(UIUtil.getContextHelpForeground());

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Cookie：", new JBScrollPane(cookieArea), true)
                .addComponentToRightColumn(hint)
                .addLabeledComponent("字体大小：", fontSpinner)
                .addLabeledComponent("每次题量（0 = 跟随粉笔账号设置）：", countSpinner)
                .addComponent(stealthBox)
                .addSeparator()
                .addLabeledComponent("计时模式：", limitCombo)
                .addLabeledComponent("整卷时长（分钟，0 = 题数 × 每题秒数）：", paperSpinner)
                .addLabeledComponent("每题秒数：", secondsSpinner)
                .addComponent(autoSubmitBox)
                .addComponent(autoPauseBox)
                .addComponent(autoResumeBox)
                .addComponent(hidePauseInfoBox)
                .addComponentToRightColumn(limitHint)
                .addSeparator()
                .addComponent(autoNextBox)
                .addComponent(pinBox)
                .addLabeledComponent("错题连续答对几次算已掌握：", streakSpinner)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private String selectedLimit() {
        int i = limitCombo.getSelectedIndex();
        return i >= 0 && i < LIMIT_VALUES.size() ? LIMIT_VALUES.get(i) : FenbiSettings.LIMIT_NONE;
    }

    @Override
    public boolean isModified() {
        FenbiSettings.Options o = FenbiSettings.options();
        return !cookieArea.getText().trim().equals(savedCookie)
                || fontSpinner.getNumber() != o.fontSize
                || countSpinner.getNumber() != o.questionCount
                || stealthBox.isSelected() != o.stealth
                || !Objects.equals(selectedLimit(), o.limitMode)
                || paperSpinner.getNumber() != o.paperMinutes
                || secondsSpinner.getNumber() != o.questionSeconds
                || autoSubmitBox.isSelected() != o.autoSubmit
                || autoPauseBox.isSelected() != o.autoPause
                || autoResumeBox.isSelected() != o.autoResume
                || hidePauseInfoBox.isSelected() != o.hidePauseInfo
                || autoNextBox.isSelected() != o.autoNext
                || pinBox.isSelected() != o.pinMaterial
                || streakSpinner.getNumber() != o.masterStreak;
    }

    @Override
    public void apply() {
        String cookie = cookieArea.getText().trim();
        FenbiSettings.setCookie(cookie);
        savedCookie = cookie;
        FenbiSettings.Options o = FenbiSettings.options();
        o.fontSize = fontSpinner.getNumber();
        o.questionCount = countSpinner.getNumber();
        o.stealth = stealthBox.isSelected();
        o.limitMode = selectedLimit();
        o.paperMinutes = paperSpinner.getNumber();
        o.questionSeconds = secondsSpinner.getNumber();
        o.autoSubmit = autoSubmitBox.isSelected();
        o.autoPause = autoPauseBox.isSelected();
        o.autoResume = autoResumeBox.isSelected();
        o.hidePauseInfo = hidePauseInfoBox.isSelected();
        o.autoNext = autoNextBox.isSelected();
        o.pinMaterial = pinBox.isSelected();
        o.masterStreak = streakSpinner.getNumber();
    }

    @Override
    public void reset() {
        String cookie = FenbiSettings.getCookie();
        savedCookie = cookie == null ? "" : cookie.trim();
        cookieArea.setText(savedCookie);
        FenbiSettings.Options o = FenbiSettings.options();
        fontSpinner.setNumber(o.fontSize);
        countSpinner.setNumber(o.questionCount);
        stealthBox.setSelected(o.stealth);
        limitCombo.setSelectedIndex(Math.max(0, LIMIT_VALUES.indexOf(o.limitMode)));
        paperSpinner.setNumber(o.paperMinutes);
        secondsSpinner.setNumber(o.questionSeconds);
        autoSubmitBox.setSelected(o.autoSubmit);
        autoPauseBox.setSelected(o.autoPause);
        autoResumeBox.setSelected(o.autoResume);
        hidePauseInfoBox.setSelected(o.hidePauseInfo);
        autoNextBox.setSelected(o.autoNext);
        pinBox.setSelected(o.pinMaterial);
        streakSpinner.setNumber(o.masterStreak);
    }

    @Override
    public void disposeUIResources() {
        cookieArea = null;
        fontSpinner = null;
        countSpinner = null;
        stealthBox = null;
        limitCombo = null;
        paperSpinner = null;
        secondsSpinner = null;
        autoSubmitBox = null;
        autoPauseBox = null;
        autoResumeBox = null;
        hidePauseInfoBox = null;
        autoNextBox = null;
        pinBox = null;
        streakSpinner = null;
    }
}
