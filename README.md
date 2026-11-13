# Orange TV

Android TV 客户端骨架。Compose for TV + Media3/ExoPlayer，点播与直播共用一套源抽象。

当前状态：**代码已完成，未编译验证**（开发机只有 JDK 17，没装 Android SDK）。
第一次用 Android Studio 打开时同步一次 Gradle 即可。

---

## 一、环境要求

| 项 | 版本 |
|---|---|
| JDK | 17（本机 `C:\Program Files\Java\jdk-17` 可用） |
| Android SDK | compileSdk 35，需在 Studio 里装 Platform 35 |
| Gradle | 由 wrapper 分发，首次同步需联网 |
| minSdk | 23（Android 6.0，覆盖绝大多数电视盒子） |

## 二、跑起来

```
1. Android Studio 打开 E:\vibeCoding\tvfans\OrangeTV
2. 等待 Gradle Sync（首次会下载依赖，几分钟）
3. 选择 app 配置，连接电视盒子（adb connect <ip>:5555）或有线设备
4. Run
```

装好后直接用遥控器操作：方向键移动焦点，OK 确认，返回键退一级。

> Gradle Wrapper 的 jar 是二进制文件，这里没法生成。已放好
> `gradle/wrapper/gradle-wrapper.properties`（指向 Gradle 8.9）。
> 若 Sync 报 `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`，
> 在 Studio 的 Terminal 里跑一次 `gradle wrapper --gradle-version 8.9`，
> 或在 Settings → Build Tools → Gradle 里选「Use Gradle from: specified location」用本地 Gradle。

---

## 三、已经实现的东西

### 1. 六个界面

| 界面 | 说明 |
|---|---|
| 首页 | 大图 Hero + 横向内容行，LazyColumn 纵向滚动，焦点自动带入视野 |
| 直播 | 左频道列表（分组 chip + 频道），右播放区，选中即播 |
| 分类 | 类型 / 地区 / 年份 / 排序 四组筛选，6 列海报网格 |
| 搜索 | 虚拟键盘 A–Z，拼音首字母匹配（TKZC → 天空之城） |
| 详情 | 海报 + 简介 + 选集网格（10 列） |
| 播放 | ExoPlayer 全屏，自绘控制条，前后 15 秒跳转 |

焦点态全部自己做（`onFocusChanged` + 缩放 + 强调色描边），没用 Material 的 ripple，
深色背景下焦点环才看得清。

### 2. 源抽象与探活（重点）

```
data/source/
  Source.kt          接口定义：RemoteSource / VodSource / LiveSource
  HealthProbe.kt     统一探活，2 秒超时，不解析业务字段
  SourceRegistry.kt  并发探活 → 按优先级选第一个可用的 → 对外暴露状态流
  M3uLiveSource.kt   用户配置的 M3U 直播源
  AssetLiveSource.kt 内置演示频道（assets/demo_live.m3u）
  DemoVodSource.kt   内置演示点播源（标题虚构，播放用公开测试流）
```

启动流程：

```
候选源按 priority 升序
   ↓ 并发 probe()，每个 2s 超时
   ↓
点播：第一个 Ok 的成为当前源 → VodState.Ready
直播：所有 Ok 源的频道合并去重 → LiveState.Ready
   ↓
全部失败 → Unavailable，界面提示去改配置
```

**明确不做的事**：不联网搜索"最新地址"、不猜镜像、不抓导航站。
地址永远来自用户配置，避免被抢注的仿冒域名顶替。

### 3. 直播源接入

用户自己的 M3U / TXT 地址放这里即可，三种格式都兼容：

```
标准 M3U:  #EXTINF:-1 tvg-logo="..." group-title="央视",CCTV-1 综合
分组 TXT:  央视频道,#genre#   然后  频道名,http://...
纯 TXT:    频道名,http://...
```

配置写进 `SourceStore`（SharedPreferences，JSON），改完调 `container.rebuild()` 立即生效。

### 4. 播放器：倍速与进度记忆

**倍速**：底部控件有一排 chip（0.5 / 0.75 / 1 / 1.25 / 1.5 / 2），选中即时生效。
选择会写回 `PlaybackStore.speed`，下次进播放器直接沿用——这是全局偏好，不是单片记忆。

**进度记忆**：键是 `vodId#line#episode`，**不含播放地址**，所以源换了域名、CDN 换了
地址，只要还是同一集，断点照样接得上。

| 时机 | 行为 |
|---|---|
| 播放中 | 每 5 秒落一次盘，异常退出最多丢 5 秒 |
| 暂停 / 返回 / 按 Home 退到后台 | 立即落盘并暂停 |
| 再次进入同一集 | 就绪后自动 seek 回断点，右上角提示「已从 xx:xx 继续播放」 |
| 进度 < 20 秒 | 不记（刚点开就走，不该留下痕迹） |
| 进度 > 片尾前 45 秒 | 视为看完，清掉断点 |
| 点「从头」 | 清掉这一集的断点 |

详情页会读 `vm.resumes`，有断点时主按钮变成「继续观看 · 第 3 集 12:34」，
旁边给一条剩余进度条，选集网格里那一集也有个强调色标记。

**遥控器**（`PlayerScreen.handleKey`，用平台 keyCode 判断，各盒子硬按键差异都在这一层吸收）：

| 键 | 行为 |
|---|---|
| ← / → | 快退 / 快进，单次 10s，连按 3 次转 30s，8 次转 60s |
| OK | 控件收起时唤出；控件在、焦点在画面上时暂停/继续 |
| ↓ | 把焦点送进底部控件 |
| ↑（焦点在倍速行） | 回到画面，继续用方向键调进度 |
| 快进/快退硬键、播放暂停硬键 | 直接响应 |

控件在播放中 4.5 秒无操作会自动收起，但只要焦点在控件上就永不收起——避免「一操作就消失」。

### 5. 手机扫码配置

电视端顶栏右侧的齿轮进入「扫码配置」页。App 会在局域网起一个极小的 HTTP 服务
（`config/LocalConfigServer.kt`，手写 HTTP/1.1，零依赖），把
`http://<电视IP>:8080..8090/?t=<令牌>` 画成二维码（ZXing core，自己转 Bitmap）。

手机扫码后在浏览器表单里填直播源 / 点播源地址（每行一个，支持「名称 | 地址」），
点「保存并同步」→ 电视端立刻落盘、重新装配源、探活，页面上的状态点
（绿=可用 / 红=失效 / 灰=未探测）随之更新。

安全边界：

| 措施 | 说明 |
|---|---|
| 会话令牌 | 每次启动随机生成，所有请求都要带 `?t=`，防止同网段别人改配置 |
| 仅监听局域网 | 只绑 `0.0.0.0` 的本机端口，无公网出口 |
| 退出即关 | `MainActivity.onDestroy`（isFinishing）关闭端口 |

电视端这一页只负责展示二维码、展示每条配置的探活状态、触发重新探活，
编辑全部在手机上完成；页面文件在 `assets/config_page.html`，
想改手机端样式直接改那个文件。

再次强调：App **不会联网搜索新地址**。地址全部来自你扫码填的内容，
全挂了就显示失败原因，等你手动更新。

---

## 四、还差的部分

| 功能 | 位置 | 说明 |
|---|---|---|
| 点播源实现 | `di/AppContainer.kt` 的 `vodFactories` | 目前只有内置演示源 |
| 拼音索引 | `util/HanziInitials.kt` | 现在用 ICU 音译器兜底，Android 10+ 有效；低版本建议引 TinyPinyin 并在入库时预计算 |
| 首页「继续观看」行 | `ui/home/HomeScreen.kt` | 数据已经有了（`vm.resumes`），只是还没铺到首页 |
| 首字母键盘长按加速 | 待做 | 体验优化 |
| 配置页电视端手动编辑 | — | 刻意不做，遥控器敲地址体验太差，一律走手机 |

### 接入自己的点播源

在 `AppContainer.rebuild()` 里加一行：

```kotlin
val vodFactories = buildList<() -> VodSource> {
    add { DemoVodSource() }
    add { MyVodSource(baseUrl = store.vodEndpoints.first().url, client = client) }
}
```

然后实现 `VodSource` 的 6 个方法。**UI 一行都不用改**——这是这套抽象的意义。

---

## 五、目录结构

```
app/src/main/java/top/orangetv/
├── MainActivity.kt              单 Activity
├── OrangeTvApp.kt               Application，持有容器
├── di/AppContainer.kt           OkHttp / Store / Registry 装配 + ConfigBridge 实现
├── config/                      扫码配置：HTTP 服务 / 页面加载器 / 桥接口
├── data/
│   ├── model/Models.kt          VodSummary / VodDetail / Channel / EpgProgram
│   ├── m3u/M3uParser.kt         三种播放列表格式解析
│   ├── settings/SourceStore.kt  配置持久化
│   ├── settings/PlaybackStore.kt 断点 + 倍速持久化
│   └── source/                  接口 + 探活 + 注册表 + 各实现
├── util/
│   ├── HanziInitials.kt         汉字转首字母
│   ├── TimeFormat.kt            时间与倍速标签格式化
│   ├── LanAddress.kt            取局域网 IPv4
│   └── QrCode.kt                ZXing 生成二维码 Bitmap
└── ui/
    ├── theme/Theme.kt           6 套配色，默认珊瑚橙
    ├── common/                  Scaffold / TvFocusable / PosterCard 等
    ├── nav/AppNav.kt            路由（含 config）
    ├── config/ConfigScreen.kt   扫码配置页
    └── home|live|category|search|detail|player/
```

`assets/`：`config_page.html`（手机端配置表单页）、`demo_live.m3u`（内置演示频道）

---

## 六、已知约束

1. **未编译验证**：所有 Compose API 按稳定版签名书写，但没跑过编译器。首次 Sync 若有报错，大概率是某个 import 或参数名，改起来很快。
2. **点播源层是空的**：`DemoVodSource` 只提供虚构标题 + 公开测试流，用来验证 UI。接真实源需要自己实现 `VodSource`。
3. **播放器支持倍速与断点续播**，但还没有字幕、码率切换、画中画。需要的话在 `PlayerScreen` 里加。
4. **live 频道没有 EPG**：`EpgProgram` 模型已经留好，接 EPG 数据源后可以直接渲染进度。
