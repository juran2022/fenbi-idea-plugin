# Fenbi Xingce —— 在 IntelliJ IDEA 里刷粉笔行测

一个 IDEA 插件：右侧工具窗口里按考点组卷、作答、交卷看解析；带限时、键盘作答、错题本和按模块统计。

工具窗口有三个标签页：**练习 / 错题本 / 统计**。

## 功能

### 练习
- 与粉笔网页版一致的行测考点目录，显示 `已做/总数`，有未完成练习的考点标「未完成」，双击继续作答
- 双击考点（或右键「开始练习」练整个模块）→ 组卷 → 作答。答案实时同步到粉笔，App 里也能看到记录
- 交卷后看解析：对错高亮、你的答案 / 正确答案、每题用时、解析、来源；`W` 跳到下一道错题

### 计时 / 限时模式（0.2 新增）
- 工具栏「计时模式」按钮或设置页切换：**不限时 / 整卷限时 / 每题限时**
- 整卷限时：时长可设（默认按「题数 × 每题秒数」折算），剩 5 分钟变黄、剩 1 分钟变红并弹提醒
- 每题限时：当前题倒计时，超时自动锁定并跳到下一题，解析页标「⏱ 超时」；底部状态栏也会显示「本题剩余 mm:ss」
- 可选「时间到自动交卷」；不开的话超时后继续计时，用时如实记录

### 暂停 / 切屏自动暂停（0.3 新增）
- **手动暂停**：卷面顶栏「⏸ 暂停」、工具栏暂停按钮或按 `空格`；再按一次 `空格`（或点一下卷面）继续
- **切屏自动暂停**（默认开启）：下面任一情况都会自动暂停
  - 切到其他程序（Alt+Tab、点了别的窗口、锁屏）
  - 刷题窗口被隐藏（包括按老板键 `Ctrl+Alt+Shift+F`、最小化工具窗口）
  - 切到「错题本 / 统计」标签页，或点「返回目录」
- **切回来自动继续**（默认关闭）：开启后，自动暂停的那次在切回卷面时自动继续；手动暂停始终要手动继续
- 暂停期间：倒计时、每题计时、总用时全部冻结，题目被遮住（防止暂停时偷看），选项和交卷快捷键都不响应
- **隐藏暂停提示**（默认关闭）：开启后暂停时卷面只剩一块空白，不显示「已暂停」、原因、进度和操作说明；底部状态栏也只留一个 `⏸`；点击或按 `空格` 照样继续
- 整卷限时、每题限时、自动交卷都按「扣掉暂停后的时间」计算；练习记录里的用时也不含暂停
- 开关在 `Settings → Tools → Fenbi 行测`，或工具栏「计时模式」菜单里

### 键盘作答（0.2 新增，先点一下卷面）
| 按键 | 作用 |
|---|---|
| `1`–`4` / `A`–`D` | 选择选项（默认选完自动跳到下一道没做的题） |
| `空格` | 暂停 / 继续计时 |
| `←` `→` / `K` `J` | 上一题 / 下一题 |
| `Home` `End` | 第一题 / 最后一题 |
| `Ctrl+Enter` | 交卷 |
| `W` | 解析页：下一道错题 |
| `M` | 材料固定在顶部 / 取消 |
| `H` | 高亮选中的文字（双击高亮处取消） |
| `?` | 快捷键帮助 |
| `Ctrl+Alt+Shift+F` | 显示/隐藏整个刷题窗口（老板键，可在 Keymap 里改） |

### 错题本（0.2 新增）
- 交卷时做错和没做的题自动收进本地错题本 `~/.fenbi-idea/wrong-book.json`（题干、选项、材料、解析都存了快照）
- 按 模块 → 考点 → 题目 分组，显示错误次数；「只看未掌握」筛选
- 双击任意一层重做：选完立即判对错并显示解析；连续答对 N 次（默认 2 次，可设）自动标记「已掌握」
- 正常练习中再次遇到错题本里的题，答对也会计入连对次数
- 支持移出选中的题（`Delete`）、一键清理已掌握

### 按模块统计（0.2 新增）
- 范围：近 7 天 / 近 30 天 / 全部
- 总览：练习时长、次数、题数、正确率、练习天数
- **练习时长 vs 正确率**：每个模块一张散点图（横轴 = 此前累计练习时长，纵轴 = 单次正确率）+ 趋势线，
  给出相关系数 r 和结论（≥0.5 明显正相关、0.3–0.5 弱正相关），并在顶部写出「正相关最明显的模块」
- 各模块累计时长、正确率（含近 5 次）、近 14 天每日练习时长（按模块堆叠）、明细表
- 每个模块至少 5 次练习才判断相关性；图表是纯 SVG，不依赖网络

### 资料分析排版优化（0.2 新增）
- 材料默认**固定在卷面顶部**（可滚动、可放大），切到同一材料的题时不用来回翻；不需要时按 `M` 或点 ✕
- 每题有「📄 材料一」标签，材料标题标明对应第几题；材料块可折叠
- 表格可横向滚动、斑马纹，数字列自动右对齐、等宽数字
- 点击图片放大看（再点一次 2 倍）；选项里的图片按住 `Alt` 点
- 暗色主题下图片自动加白底（公式图片多是透明底黑字，否则看不清）
- `H` 高亮关键数据

### 其他
- 低调模式：卷面用编辑器字体和配色，图片半透明（鼠标移上去恢复）
- Cookie 存在 IDE 的 Password Safe，不写进明文配置
- 练习记录 `~/.fenbi-idea/practice-log.csv`：时间、模块、考点、题数、已答、正确、正确率(%)、用时(秒)，Excel 可直接打开

## 运行 / 安装

环境：IntelliJ IDEA 2026.2+，JDK 25（不用另装，用 IDEA 自带的 `jbr` 就行）。
2026.2 的平台类已经是 Java 25 字节码，用 JDK 17 编译会报 `class file has wrong version 69.0`。

1. 用 IDEA 打开**包含 `build.gradle.kts` 的这一层目录**（`fenbi-idea-plugin\fenbi-idea-plugin`，不是外面那层），等 Gradle 同步完
2. 打包：右侧 Gradle 面板 → `fenbi-idea-plugin` → `Tasks` → `intellij platform` → `buildPlugin`
   （也可以按两下 `Ctrl` 打开 Run Anything，输入 `gradle buildPlugin` 回车）
3. 生成 `build/distributions/fenbi-idea-plugin-0.3.1.zip`，
   `Settings → Plugins → ⚙ → Install Plugin from Disk…` 选这个 zip，重启 IDE（会覆盖旧版本）
4. 调试（可选）：同一个分组里的 `runIde`，会启动一个装好插件的沙箱 IDE

`gradle.properties` 里有两行是跟本机路径绑定的，换机器要改：

```properties
# 直接拿本机装的 IDEA 当编译目标平台，省掉下载 ~1.5 GB 的平台包；删掉这行则改为在线下载 2026.2.3
ideaHome=D:\\IntelliJ IDEA 2026.2.3
# JDK 25 工具链，指向 IDEA 自带的 jbr
org.gradle.java.installations.paths=D:\\IntelliJ IDEA 2026.2.3\\jbr
```

### Gradle 面板里没有 `intellij platform` 分组？

只看到 `build setup / help / other` 这几组，说明 Gradle 没有加载到 IntelliJ Platform Gradle Plugin
（本项目之前 `build.gradle.kts` 是 0 字节的空文件，就是这个原因）。现在脚本已经补好，按下面做：

- 确认打开的是含 `build.gradle.kts` 的那层目录（解压后外面还套着一层同名文件夹）
- 点 Gradle 面板左上角的刷新按钮（Reload All Gradle Projects），然后看底部 `Build` 窗口的 `Sync` 标签有没有报错
- 同步报错的话，把 `Sync` 标签里的报错贴出来

## 首次使用

1. 浏览器登录 https://www.fenbi.com ，进入行测题库
2. `F12` → Network → 点开任意一个 `tiku.fenbi.com` 的请求 → Request Headers → 复制 `Cookie` 的整段值
3. IDEA `Settings → Tools → Fenbi 行测`，粘贴 Cookie；按需设置字体、题量、计时模式等
4. 打开右侧 **Fenbi** 工具窗口 → 「练习」→ 双击考点开始

Cookie 过期（提示 401/403）时重新复制一次即可。

## 代码结构

```
src/main/java/com/ranju/fenbi/
├── api/        FenbiClient（粉笔接口）、Models、FenbiException
├── settings/   FenbiSettings（设置 + Password Safe）、FenbiConfigurable（设置页）
├── book/       WrongBook（本地错题本）
├── log/        PracticeLog（练习记录 CSV 读写）
├── stats/      Stats（按模块统计、相关系数、回归）
├── util/       Listeners（数据变更通知）
├── actions/    BossKeyAction（老板键）
└── ui/
    ├── FenbiToolWindowFactory  三个标签页
    ├── FenbiPanel              练习：目录树 + 卷面 + 计时
    ├── WrongBookPanel          错题本
    ├── StatsPanel / StatsRenderer   统计页（SVG 图表）
    ├── Sheet / SheetRenderer   卷面数据 → HTML（作答 / 解析 / 错题重做）
    ├── Session                 一次练习的状态
    ├── WebView                 JCEF 浏览器 + JS 回调
    └── SheetStyle、SimpleAct、Ui
src/main/resources/sheet/
├── sheet.css   卷面样式
└── sheet.js    键盘作答、计时、材料固定、高亮、看大图
```

用到的接口（都在 `https://tiku.fenbi.com`，统一带 `app=web&kav=100&av=100&hav=100&version=3.0.0.0`）：

| 用途 | 方法 | 路径 |
|---|---|---|
| 考点目录 | GET | `/api/xingce/categories?filter=keypoint` |
| 未完成练习 | GET | `/api/xingce/category-exercises-unfinished` |
| 个人组卷设置 | GET | `/activity/userquiz/getUserQuestionInfo` |
| 组卷 | POST 表单 | `/api/xingce/exercises`（type=3, keypointId, limit…） |
| 练习详情 / 已答 | GET | `/api/xingce/exercises/{id}` |
| 题目 | GET | `/api/xingce/universal/auth/questions?type=0&id={id}` |
| 作答 | POST JSON | `/api/xingce/async/exercises/{id}/incr` |
| 交卷 | POST 表单 | `/api/xingce/async/exercises/{id}/submit`（status=3） |
| 解析 | GET | `/api/xingce/universal/auth/solutions?type=0&id={id}` |

## 注意

- IDEA 2026.2 起 JCEF 成了独立的内置插件「Web Browser (JCEF)」，本插件在 plugin.xml 里依赖 `com.intellij.modules.jcef`；如果提示缺少这个依赖，到 Settings → Plugins → Installed 里确认它没被禁用

- 这些接口是粉笔网页版内部接口，不是公开 API，参考自开源项目 [maoguy/fenbi-client](https://github.com/maoguy/fenbi-client)（VS Code 版，MIT 协议）。粉笔改版后可能失效，届时需要用 F12 抓包更新 `FenbiClient`
- 使用第三方客户端可能不符合粉笔的用户协议，账号风险请自行评估；不要把 Cookie 发给别人
- 目前只支持单选题（行测基本都是单选）；其他题型会提示去网页作答
- 错题重做在本地判分，不会同步到粉笔的错题本
- 统计里的「用时」是从开卷到交卷、扣掉暂停后的时长；关掉「切屏自动暂停」又没手动暂停的话，中途离开也会算进去
- 自动暂停只检测「离开 IDEA」和「离开刷题页面」；在同一个 IDEA 里切到另一个项目窗口、或只是把焦点点到代码编辑器，不会暂停

<a id="support"></a>

## 支持这个项目

这个插件免费使用，所有功能都对所有人开放，打赏不会解锁任何东西。

如果它帮你在刷题上省了点时间，这些都是很实在的支持：

- 点一个 ⭐ Star，让更多在备考的人能看到它
- 遇到问题或有想法，开一个 [Issue](../../issues)
- 推荐给身边同样在备考的朋友

如果你还想请我喝杯咖啡，非常感谢 🙏 这会让我更有动力在粉笔改版后及时修好它，继续打磨新功能。

<details>
<summary>☕ 微信 / 支付宝（点击展开）</summary>

<br>

<table align="center">
  <tr>
    <td align="center"><img src="docs/images/support/wechat.png" height="300" alt="微信收款码"></td>
    <td align="center"><img src="docs/images/support/alipay.png" height="300" alt="支付宝收款码"></td>
  </tr>
</table>

<p align="center"><sub>多少都是心意，扫码后请核对收款人信息。备注里不要填账号、Cookie 等敏感信息。</sub></p>

</details>

<br>

祝备考顺利，早日上岸！

## License

本项目采用 [MIT License](LICENSE) 开源。
