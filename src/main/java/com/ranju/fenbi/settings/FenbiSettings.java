package com.ranju.fenbi.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/** 普通设置存在 fenbi-idea.xml；Cookie 存在 IDE 的 Password Safe 里。 */
@Service(Service.Level.APP)
@State(name = "FenbiIdeaSettings", storages = @Storage("fenbi-idea.xml"))
public final class FenbiSettings implements PersistentStateComponent<FenbiSettings.Options> {

    public static final String LIMIT_NONE = "NONE";
    public static final String LIMIT_PAPER = "PAPER";
    public static final String LIMIT_QUESTION = "QUESTION";

    public static class Options {
        public int fontSize = 13;
        /** 每次题量，0 = 跟随粉笔账号设置 */
        public int questionCount = 10;
        /** 低调模式：编辑器字体和配色，图片半透明 */
        public boolean stealth = false;

        /** 计时模式：NONE / PAPER（整卷限时）/ QUESTION（每题限时） */
        public String limitMode = LIMIT_NONE;
        /** 整卷时长（分钟），0 = 题数 × 每题秒数 */
        public int paperMinutes = 0;
        /** 每题秒数（每题限时用；整卷限时且 paperMinutes = 0 时用来折算） */
        public int questionSeconds = 60;
        /** 时间到自动交卷 */
        public boolean autoSubmit = true;
        /** 切屏自动暂停：切到其他程序、隐藏刷题窗口、切到其他标签页或返回目录时暂停计时 */
        public boolean autoPause = true;
        /** 切回来自动继续（只对自动暂停生效；手动暂停总是要手动继续） */
        public boolean autoResume = false;
        /** 暂停时不显示暂停提示（遮罩只留一块空白，不显示“已暂停”、原因、进度和操作说明） */
        public boolean hidePauseInfo = false;

        /** 选完选项自动跳到下一道没做的题 */
        public boolean autoNext = true;
        /** 资料分析等有材料的题：材料默认固定在卷面顶部 */
        public boolean pinMaterial = true;

        /** 错题连续答对几次算“已掌握” */
        public int masterStreak = 2;
    }

    private Options options = new Options();

    public static FenbiSettings getInstance() {
        return ApplicationManager.getApplication().getService(FenbiSettings.class);
    }

    public static Options options() {
        return getInstance().getState();
    }

    @Override
    public @NotNull Options getState() {
        return options;
    }

    @Override
    public void loadState(@NotNull Options state) {
        this.options = state;
    }

    /** 当前计时模式的中文说明。 */
    public static String limitDescription() {
        Options o = options();
        return switch (o.limitMode == null ? LIMIT_NONE : o.limitMode) {
            case LIMIT_PAPER -> o.paperMinutes > 0
                    ? "整卷限时 " + o.paperMinutes + " 分钟"
                    : "整卷限时（每题 " + o.questionSeconds + " 秒折算）";
            case LIMIT_QUESTION -> "每题限时 " + o.questionSeconds + " 秒";
            default -> "不限时";
        };
    }

    private static CredentialAttributes cookieAttributes() {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("FenbiIdea", "cookie"));
    }

    public static String getCookie() {
        return PasswordSafe.getInstance().getPassword(cookieAttributes());
    }

    public static void setCookie(String cookie) {
        String value = cookie == null || cookie.isBlank() ? null : cookie.trim();
        PasswordSafe.getInstance().setPassword(cookieAttributes(), value);
    }
}
