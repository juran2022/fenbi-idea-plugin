package com.ranju.fenbi.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.function.BooleanSupplier;

/** 工具栏按钮：一个动作 + 一个“是否可用”的判断。 */
final class SimpleAct extends DumbAwareAction {
    private final Runnable action;
    private final BooleanSupplier enabled;

    SimpleAct(String text, Icon icon, Runnable action, BooleanSupplier enabled) {
        super(text, text, icon);
        this.action = action;
        this.enabled = enabled;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        action.run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(enabled.getAsBoolean());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
