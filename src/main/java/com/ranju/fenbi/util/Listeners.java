package com.ranju.fenbi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 极简的变更通知：数据变了就在 EDT 上回调所有监听者。 */
public final class Listeners {
    private final List<Runnable> list = new CopyOnWriteArrayList<>();

    public void add(Disposable parent, Runnable listener) {
        list.add(listener);
        Disposer.register(parent, () -> list.remove(listener));
    }

    public void fire() {
        Application app = ApplicationManager.getApplication();
        for (Runnable r : list) app.invokeLater(r, ModalityState.any());
    }
}
