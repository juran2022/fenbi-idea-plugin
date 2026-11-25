package com.ranju.fenbi.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.ranju.fenbi.settings.FenbiSettings;

/** 卷面的字体和配色，跟随 IDE 主题。 */
record SheetStyle(int fontSize, String fontFamily, String foreground, String background,
                  boolean stealth, boolean dark) {

    static SheetStyle current() {
        FenbiSettings.Options o = FenbiSettings.options();
        boolean dark = !JBColor.isBright();
        if (o.stealth) {
            EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
            return new SheetStyle(o.fontSize,
                    "'" + scheme.getEditorFontName() + "', monospace",
                    ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()),
                    ColorUtil.toHtmlColor(scheme.getDefaultBackground()),
                    true, dark);
        }
        return new SheetStyle(o.fontSize,
                "-apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif",
                ColorUtil.toHtmlColor(UIUtil.getLabelForeground()),
                ColorUtil.toHtmlColor(UIUtil.getPanelBackground()),
                false, dark);
    }

    /** 写进页面 :root 的 CSS 变量。 */
    String cssVars() {
        return ":root{--fs:" + fontSize + "px;--font:" + fontFamily + ";--fg:" + foreground + ";--bg:" + background + ";}";
    }
}
