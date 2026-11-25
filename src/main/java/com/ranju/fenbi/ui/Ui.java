package com.ranju.fenbi.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.ranju.fenbi.api.FenbiException;
import com.ranju.fenbi.log.PracticeLog;

import java.util.function.Consumer;

/** 线程、通知等小工具。 */
final class Ui {
    private static final Logger LOG = Logger.getInstance(Ui.class);

    private Ui() {
    }

    @FunctionalInterface
    interface Task {
        void run() throws Exception;
    }

    /** 在后台线程执行；出错时把错误信息交给 onError（仍在后台线程）。 */
    static void background(Task task, Consumer<String> onError) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                task.run();
            } catch (FenbiException e) {
                onError.accept(e.getMessage());
            } catch (Exception e) {
                LOG.warn(e);
                onError.accept(e.toString());
            }
        });
    }

    static void edt(Project project, Runnable r) {
        ApplicationManager.getApplication().invokeLater(r, ModalityState.any(), o -> project.isDisposed());
    }

    static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup("Fenbi")
                .createNotification(message, type)
                .notify(project);
    }

    static void openLog(Project project) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(PracticeLog.file());
        if (vf == null) {
            Messages.showInfoMessage(project, "还没有练习记录。交一次卷后会生成：\n" + PracticeLog.file(), "练习记录");
            return;
        }
        FileEditorManager.getInstance(project).openFile(vf, true);
    }

    static String mmss(long seconds) {
        long s = Math.max(0, seconds);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
