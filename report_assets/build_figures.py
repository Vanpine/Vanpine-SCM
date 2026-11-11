# -*- coding: utf-8 -*-
"""生成课程设计报告所需的 SVG 图表。"""
import os, html, math

OUT = os.path.join(os.path.dirname(__file__), "fig")
os.makedirs(OUT, exist_ok=True)

FONT = "PingFang SC, Microsoft YaHei, SimSun, sans-serif"
C = {
    "ink": "#1f2937",
    "primary": "#1f4e79",
    "primary_l": "#dbe5f1",
    "blue": "#2e75b6",
    "blue_l": "#e8f1fb",
    "green": "#548235",
    "green_l": "#e2efda",
    "orange": "#c55a11",
    "orange_l": "#fbe5d6",
    "purple": "#7030a0",
    "purple_l": "#eee4f6",
    "gray": "#7f7f7f",
    "gray_l": "#f2f2f2",
    "line": "#6b7280",
    "red": "#c00000",
    "red_l": "#fbeaea",
}


class SVG:
    def __init__(self, w, h, bg="#ffffff"):
        self.w, self.h = w, h
        self.parts = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
            f'<rect width="{w}" height="{h}" fill="{bg}"/>',
            '''<defs>
              <marker id="arr" markerWidth="10" markerHeight="10" refX="9" refY="3.2" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6.4 L9,3.2 z" fill="#6b7280"/>
              </marker>
              <marker id="arrb" markerWidth="10" markerHeight="10" refX="9" refY="3.2" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6.4 L9,3.2 z" fill="#2e75b6"/>
              </marker>
              <marker id="arrg" markerWidth="10" markerHeight="10" refX="9" refY="3.2" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6.4 L9,3.2 z" fill="#548235"/>
              </marker>
            </defs>''',
        ]

    def add(self, s):
        self.parts.append(s)

    def text(self, x, y, s, size=16, anchor="middle", color=None, weight="normal",
             opacity=1.0, family=FONT):
        color = color or C["ink"]
        self.add(
            f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="{family}" '
            f'font-size="{size}" fill="{color}" font-weight="{weight}" opacity="{opacity}">{html.escape(str(s))}</text>'
        )

    def lines(self, x, y, lines, size=16, anchor="middle", color=None, lh=1.35,
              weight="normal"):
        color = color or C["ink"]
        for i, s in enumerate(lines):
            self.text(x, y + i * size * lh, s, size, anchor, color, weight)

    def rect(self, x, y, w, h, fill="#fff", stroke=None, rx=8, sw=1.6, dash=None):
        stroke = stroke or C["line"]
        d = f' stroke-dasharray="{dash}"' if dash else ""
        self.add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" '
                 f'stroke="{stroke}" stroke-width="{sw}"{d}/>')

    def box(self, x, y, w, h, title, sub=None, fill=None, stroke=None, size=17,
            tcolor=None, rx=9, dash=None):
        fill = fill or C["blue_l"]
        stroke = stroke or C["blue"]
        self.rect(x, y, w, h, fill, stroke, rx, dash=dash)
        if sub:
            self.text(x + w / 2, y + h / 2 - 6, title, size, color=tcolor or C["primary"], weight="bold")
            self.text(x + w / 2, y + h / 2 + 18, sub, size - 3, color=C["ink"])
        else:
            self.text(x + w / 2, y + h / 2 + size * 0.35, title, size, color=tcolor or C["primary"], weight="bold")

    def line(self, x1, y1, x2, y2, color=None, sw=1.6, dash=None, marker=False):
        color = color or C["line"]
        d = f' stroke-dasharray="{dash}"' if dash else ""
        m = ' marker-end="url(#arr)"' if marker else ""
        self.add(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
                 f'stroke-width="{sw}"{d}{m}/>')

    def path(self, d, color=None, sw=1.6, dash=None, marker=True, fill="none"):
        color = color or C["line"]
        dd = f' stroke-dasharray="{dash}"' if dash else ""
        m = ' marker-end="url(#arr)"' if marker else ""
        self.add(f'<path d="{d}" fill="{fill}" stroke="{color}" stroke-width="{sw}"{dd}{m}/>')

    def arrow(self, x1, y1, x2, y2, label=None, color=None, sw=1.6, dash=None,
              lsize=13, lcolor=None, marker=True):
        self.line(x1, y1, x2, y2, color, sw, dash, marker=marker)
        if label:
            self.text((x1 + x2) / 2, (y1 + y2) / 2 - 7, label, lsize, color=lcolor or C["ink"])

    def ellipse(self, cx, cy, rx, ry, title, fill=None, stroke=None, size=15, sub=None):
        fill = fill or "#fff"
        stroke = stroke or C["blue"]
        self.add(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{fill}" stroke="{stroke}" stroke-width="1.6"/>')
        if sub:
            self.text(cx, cy - 2, title, size, color=C["primary"], weight="bold")
            self.text(cx, cy + 18, sub, size - 3, color=C["ink"])
        else:
            self.text(cx, cy + size * 0.35, title, size, color=C["primary"])

    def diamond(self, cx, cy, w, h, title, size=14, fill=None):
        fill = fill or C["orange_l"]
        pts = f"{cx},{cy-h/2} {cx+w/2},{cy} {cx},{cy+h/2} {cx-w/2},{cy}"
        self.add(f'<polygon points="{pts}" fill="{fill}" stroke="{C["orange"]}" stroke-width="1.6"/>')
        self.text(cx, cy + size * 0.35, title, size, color=C["orange"], weight="bold")

    def actor(self, cx, cy, name, color=None, scale=1.0, sub=None):
        color = color or C["primary"]
        s = scale
        # head
        self.add(f'<circle cx="{cx}" cy="{cy-46*s}" r="{14*s}" fill="#fff" stroke="{color}" stroke-width="2"/>')
        # body
        self.line(cx, cy - 32 * s, cx, cy + 8 * s, color, 2, marker=False)
        self.line(cx - 20 * s, cy - 16 * s, cx + 20 * s, cy - 16 * s, color, 2, marker=False)
        self.line(cx, cy + 8 * s, cx - 16 * s, cy + 34 * s, color, 2, marker=False)
        self.line(cx, cy + 8 * s, cx + 16 * s, cy + 34 * s, color, 2, marker=False)
        self.text(cx, cy + 62 * s, name, 17, color=color, weight="bold")
        if sub:
            self.text(cx, cy + 84 * s, sub, 14, color=C["ink"])

    def title(self, text):
        self.text(self.w / 2, 46, text, 26, color=C["primary"], weight="bold")

    def save(self, name):
        self.parts.append("</svg>")
        p = os.path.join(OUT, name + ".svg")
        with open(p, "w", encoding="utf-8") as f:
            f.write("\n".join(self.parts))
        return p


def wcn(s, n):
    """粗略按中文字符宽度折行。"""
    out, cur, width = [], "", 0.0
    for ch in s:
        w = 1.0 if ord(ch) > 127 else 0.55
        if width + w > n:
            out.append(cur)
            cur, width = ch, w
        else:
            cur += ch
            width += w
    if cur:
        out.append(cur)
    return out


# ---------------------------------------------------------------- 图3-1 系统用例图
def fig_3_1():
    s = SVG(1600, 1040)
    s.rect(280, 130, 1040, 850, "#fbfcfe", C["primary"], rx=14, sw=2)
    s.text(306, 178, "VANPINE SRM 全球供应商管理系统", 20, color=C["primary"], weight="bold", anchor="start")
    # admin actor top center
    s.actor(800, 55, "平台管理员")
    # top row (admin)
    top = [(560, 250, "智能供应商匹配"), (800, 250, "平台运营管理"), (1040, 250, "风险监控与预警")]
    for x, y, t in top:
        s.ellipse(x, y, 112, 42, t, C["orange_l"], C["orange"], 14)
        s.line(800, 112, x, y - 42, "#d3a45f", 1.1)
    # left column (buyer)
    left = [(520, 380, "入驻申请与审核"), (520, 510, "发布采购需求"), (520, 640, "询盘管理"),
            (520, 770, "比价与授标"), (520, 900, "电子合同签署")]
    for x, y, t in left:
        s.ellipse(x, y, 112, 42, t, C["blue_l"], C["blue"], 14)
    s.actor(110, 640, "采购企业用户")
    for x, y, t in left:
        s.line(168, 640, x - 106, y, "#9db4cc", 1.1)
    # right column (supplier)
    right = [(1080, 380, "企业画像维护"), (1080, 510, "接收询盘与报价"), (1080, 640, "合同确认"),
             (1080, 770, "组织发货"), (1080, 900, "收货与验收")]
    for x, y, t in right:
        s.ellipse(x, y, 112, 42, t, C["green_l"], C["green"], 14)
    s.actor(1490, 640, "供应商用户")
    for x, y, t in right:
        s.line(1432, 640, x + 106, y, "#9dbf9a", 1.1)
    # dd firm bottom
    s.ellipse(800, 900, 112, 42, "尽职调查", C["purple_l"], C["purple"], 14)
    s.actor(110, 900, "尽调机构")
    s.line(168, 900, 682, 900, "#c9a8d6", 1.1)
    s.text(800, 1010, "图3-1  系统总体用例图", 15, color=C["gray"])
    return s.save("fig3-1")


# ---------------------------------------------------------------- 图3-2 子用例
def two_actor_usecase(fid, name, caption, actor1, cases1, actor2, cases2, color2=None, stroke2=None):
    s = SVG(1400, 860)
    s.rect(300, 80, 800, 700, "#fbfcfe", C["primary"], rx=14, sw=2)
    s.text(700, 122, caption, 20, color=C["primary"], weight="bold")
    color2 = color2 or C["green_l"]
    stroke2 = stroke2 or C["green"]
    def place(x, cases):
        n = len(cases)
        ys = [190 + i * (540 / max(1, n - 1)) for i in range(n)] if n > 1 else [440]
        out = []
        for y, t in zip(ys, cases):
            s.ellipse(x, y, 108, 40, t, C["blue_l"] if x < 700 else color2,
                      C["blue"] if x < 700 else stroke2, 13.5)
            out.append((x, y))
        return out
    p1 = place(480, cases1)
    p2 = place(1040, cases2)
    y1 = sum(y for _, y in p1) / len(p1)
    y2 = sum(y for _, y in p2) / len(p2)
    s.actor(120, y1, actor1)
    for x, y in p1:
        s.line(178, y1, x - 102, y, "#9db4cc", 1.1)
    s.actor(1380, y2, actor2)
    for x, y in p2:
        s.line(1322, y2, x + 102, y, "#9dbf9a", 1.1)
    s.text(700, 825, name, 15, color=C["gray"])
    return s.save(fid)


def fig_3_2():
    return two_actor_usecase(
        "fig3-2", "图3-2  入驻审核与租户管理子用例图", "入驻审核与租户管理",
        "申请企业", ["提交入驻申请", "上传资质材料", "查看审核进度"],
        "平台管理员", ["制裁名单筛查", "审核入驻申请", "开通企业租户", "初始化管理员", "分配角色菜单", "停用/启用租户"],
    )


def fig_3_3():
    return two_actor_usecase(
        "fig3-3", "图3-3  供应商画像与智能匹配子用例图", "供应商画像与智能匹配",
        "供应企业", ["维护企业基本信息", "维护品类与产能", "上传认证资质", "画像完整度计算"],
        "采购员", ["发布采购需求", "需求语义解析", "向量召回候选", "多维评分排序", "查看匹配理由", "发起供需对接"],
    )


def fig_3_4():
    return two_actor_usecase(
        "fig3-4", "图3-4  询盘报价与比价授标子用例图", "询盘报价与比价授标",
        "采购员", ["创建询盘", "邀请目标供应商", "设置报价截止", "查看报价汇总", "TCO 比价分析", "确认授标", "生成授标理由"],
        "供应商", ["查看询盘", "提交报价", "修改或撤回报价"],
    )


def fig_3_5():
    s = two_actor_usecase(
        "fig3-5", "图3-5  合同与交付履约子用例图", "合同与交付履约",
        "采购员", ["创建电子合同", "上传签署件", "合同确认", "收货确认", "到货验收", "风险处置"],
        "供应商", ["确认合同", "创建交付单", "发货与物流登记", "退换货处理"],
    )
    return s


# ---------------------------------------------------------------- 图4-1 业务架构
def fig_4_1():
    s = SVG(1600, 900)
    s.text(800, 52, "VANPINE SRM 业务架构", 24, color=C["primary"], weight="bold")
    # swimlane labels
    s.rect(40, 100, 1520, 170, "#f7fbff", C["blue"], rx=12)
    s.text(120, 190, "采购\n企业", 17, color=C["blue"], weight="bold")
    s.rect(40, 630, 1520, 170, "#f6fbf6", C["green"], rx=12)
    s.text(120, 720, "供应\n企业", 17, color=C["green"], weight="bold")
    s.rect(40, 300, 1520, 300, "#fffdf8", C["orange"], rx=12)
    s.text(92, 455, "平\n台\n能\n力", 16, color=C["orange"], weight="bold")
    steps = ["需求发布", "智能匹配", "询盘邀请", "在线报价", "比价授标", "电子合同", "交付履约", "风险监控"]
    xs = [200 + i * 168 for i in range(8)]
    for i, (x, t) in enumerate(zip(xs, steps)):
        s.box(x, 160, 138, 62, t, fill=C["blue_l"], stroke=C["blue"], size=15)
        if i < 7:
            s.arrow(x + 138, 191, x + 168, 191, color=C["blue"])
    # platform capability band
    caps = ["多租户与RBAC", "供应商画像库", "AI匹配与理由", "TCO比价引擎", "风险信号聚合", "电子存证"]
    for i, cap in enumerate(caps):
        x = 210 + i * 225
        s.box(x, 390, 190, 54, cap, fill=C["orange_l"], stroke=C["orange"], size=14)
    # buyer steps link to platform
    for x in [268, 772, 940]:
        s.arrow(x, 300, x + 40, 390, color=C["orange"], dash="4,3", sw=1.2)
    # supplier band
    sss = ["企业画像维护", "接收询盘", "提交报价", "合同确认", "组织发货", "售后与评价"]
    for i, t in enumerate(sss):
        x = 210 + i * 225
        s.box(x, 690, 190, 54, t, fill=C["green_l"], stroke=C["green"], size=14)
    s.text(800, 855, "平台以「交易全链路 + AI 能力 + 风险信号」支撑采购方与供应方的双向协同", 16, color=C["gray"])
    return s.save("fig4-1")


# ---------------------------------------------------------------- 图4-2 逻辑架构
def fig_4_2():
    s = SVG(1500, 1080)
    s.text(750, 46, "系统逻辑架构", 24, color=C["primary"], weight="bold")
    layers = [
        ("用户访问层", 110, 90, "#f2f6fb", C["blue"]),
        ("接入层", 230, 90, "#eef4fb", C["blue"]),
        ("业务服务层", 350, 190, "#eaf2fa", C["primary"]),
        ("能力支撑层", 570, 150, "#fff6ec", C["orange"]),
        ("数据存储层", 750, 150, "#eef8ee", C["green"]),
        ("外部服务层", 930, 120, "#f5eef8", C["purple"]),
    ]
    for name, y, h, fill, stroke in layers:
        s.rect(60, y, 1380, h, fill, stroke, rx=10, sw=1.4)
        s.text(128, y + h / 2 + 6, name, 17, color=stroke, weight="bold")
    # access
    s.box(330, 128, 300, 48, "PC 浏览器 / 后台管理界面", fill="#fff", stroke=C["blue"], size=15)
    s.box(760, 128, 300, 48, "Nginx 静态资源与反向代理", fill="#fff", stroke=C["blue"], size=15)
    s.arrow(630, 152, 760, 152, color=C["blue"])
    s.box(560, 240, 620, 58, "Spring Cloud Gateway：路由转发 / JWT 鉴权 / CORS / 内部接口拦截", fill="#fff", stroke=C["blue"], size=15)
    s.arrow(940, 176, 870, 240, color=C["blue"])
    # business services
    services = [("平台服务", "认证·租户·权限\n入驻·审计"), ("供应商服务", "画像·尽调\n制裁筛查·匹配"),
                ("交易服务", "需求·询盘·报价\n比价·授标·合同"), ("交付服务", "发货·收货·验收\n物流·风险"),
                ("AI 服务", "对话·知识库\n工具调用")]
    xs = [225, 475, 725, 975, 1225]
    for (t, sub), x in zip(services, xs):
        s.rect(x, 372, 210, 90, "#fff", C["primary"], rx=9)
        s.text(x + 105, 403, t, 16, color=C["primary"], weight="bold")
        s.lines(x + 105, 430, sub.split("\n"), 13, color=C["ink"], lh=1.4)
        s.arrow(870 if x < 800 else 870, 298, x + 105, 372, color=C["primary"], sw=1.3)
    # support
    sup = ["统一认证\n与令牌", "多租户\n数据隔离", "消息通知\nRabbitMQ", "文件服务\n对象存储", "审计日志\n与链路追踪", "分布式锁\n与定时任务"]
    for i, t in enumerate(sup):
        x = 230 + i * 198
        s.box(x, 596, 180, 70, t.split("\n")[0], sub=t.split("\n")[1], fill="#fff", stroke=C["orange"], size=14, tcolor=C["orange"])
    # data
    data = ["MySQL 8\n业务数据", "Redis\n缓存/令牌", "RabbitMQ\n异步消息", "Milvus\n向量库", "OSS/MinIO\n附件存储"]
    for i, t in enumerate(data):
        x = 220 + i * 250
        s.box(x, 772, 210, 62, t.split("\n")[0], sub=t.split("\n")[1], fill="#fff", stroke=C["green"], size=14, tcolor=C["green"])
    # external
    ext = ["Embedding API\nbge-m3", "DeepSeek\n大模型推理", "天气/制裁\n数据源", "电子签/支付\n回调接口"]
    for i, t in enumerate(ext):
        x = 230 + i * 235
        s.box(x, 952, 220, 62, t.split("\n")[0], sub=t.split("\n")[1], fill="#fff", stroke=C["purple"], size=14, tcolor=C["purple"])
    s.text(750, 1050, "各层单向依赖：业务服务 → 能力支撑 → 数据存储；AI 服务与外部模型通过内网接口解耦", 14, color=C["gray"])
    return s.save("fig4-2")


# ---------------------------------------------------------------- 图4-3 技术架构
def fig_4_3():
    s = SVG(1600, 1080)
    s.text(800, 46, "系统技术架构", 24, color=C["primary"], weight="bold")
    blocks = [
        ("前端展现层", 90, ["Vue 3.5", "Vite 8", "Element Plus", "Pinia / Vue Router", "Vue I18n / Tailwind"], C["blue"], C["blue_l"]),
        ("网关与安全层", 230, ["Spring Cloud Gateway", "JWT 鉴权", "Sentinel 限流", "CORS", "内部接口白名单"], C["primary"], C["primary_l"]),
        ("业务服务层", 370, ["Spring Boot 3.3.7", "Spring Cloud Alibaba", "OpenFeign", "MyBatis-Plus 3.5.7", "Knife4j"], C["primary"], C["primary_l"]),
        ("中间件与数据层", 510, ["Nacos 注册配置", "RabbitMQ", "Redis", "MySQL 8", "Zipkin 链路追踪"], C["orange"], C["orange_l"]),
        ("AI 能力层", 650, ["Python 3.9+", "FastAPI", "LangChain", "Milvus", "DeepSeek / bge-m3"], C["purple"], C["purple_l"]),
        ("工程与部署层", 790, ["Maven 多模块", "Docker Compose", "systemd", "Nginx", "Git"], C["green"], C["green_l"]),
    ]
    for name, y, items, stroke, fill in blocks:
        s.rect(70, y, 1460, 110, fill, stroke, rx=12, sw=1.5)
        s.text(175, y + 55, name, 18, color=stroke, weight="bold")
        n = len(items)
        w = 200 if n >= 5 else 220
        gap = 18
        total = n * w + (n - 1) * gap
        x0 = 70 + 1460 - total - 40
        for i, it in enumerate(items):
            x = x0 + i * (w + gap)
            s.rect(x, y + 22, w, 66, "#fff", stroke, rx=8, sw=1.2)
            s.text(x + w / 2, y + 63, it, 14, color=C["ink"])
    # vertical arrows
    for y in [200, 340, 480, 620, 760]:
        s.arrow(800, y, 800, y + 30, color=C["line"], sw=1.4)
    s.text(800, 970, "后端按业务域拆分为 5 个 Spring Boot 微服务，统一由网关暴露；AI 计算下沉至独立 Python 服务", 15, color=C["gray"])
    return s.save("fig4-3")


# ---------------------------------------------------------------- 图4-4 网络架构
def fig_4_4():
    s = SVG(1600, 940)
    s.text(800, 46, "系统部署与网络架构", 24, color=C["primary"], weight="bold")
    # zones
    s.rect(40, 90, 1520, 150, "#f7fbff", C["blue"], rx=12, dash="6,4")
    s.text(120, 175, "公网访问区", 16, color=C["blue"], weight="bold")
    s.box(250, 115, 260, 80, "采购/供应企业浏览器", sub="HTTPS", fill="#fff", stroke=C["blue"], size=16)
    s.box(640, 115, 260, 80, "Nginx 反向代理", sub="TLS 终止 / 静态资源", fill="#fff", stroke=C["blue"], size=16)
    s.box(1030, 115, 260, 80, "运维与监控终端", sub="日志 / 指标", fill="#fff", stroke=C["blue"], size=16)
    s.arrow(510, 155, 640, 155, color=C["blue"])

    s.rect(40, 280, 1520, 275, "#eef4fb", C["primary"], rx=12, dash="6,4")
    s.text(120, 430, "应用服务区", 16, color=C["primary"], weight="bold")
    s.box(220, 340, 260, 72, "API 网关", sub="gsc-gateway :18080", fill="#fff", stroke=C["primary"], size=17)
    s.arrow(900, 195, 420, 340, color=C["primary"], sw=1.5)
    svcs = [("平台服务", "3081"), ("供应商服务", "3082"), ("交易服务", "3083"), ("交付服务", "3084"), ("AI 服务", "3085")]
    for i, (n, port) in enumerate(svcs):
        x = 560 + (i % 3) * 290
        y = 320 + (i // 3) * 100
        s.box(x, y, 240, 70, n, sub=f":{port}", fill="#fff", stroke=C["primary"], size=15)
        s.arrow(480, 376, x, y + 35, color=C["primary"], sw=1.2)
    s.box(1140, 420, 280, 70, "Python AI 服务", sub="FastAPI :8100", fill="#fff", stroke=C["purple"], size=15, tcolor=C["purple"])
    s.arrow(1090, 455, 1140, 455, color=C["purple"], sw=1.2)

    s.rect(40, 585, 1520, 160, "#f6fbf6", C["green"], rx=12, dash="6,4")
    s.text(120, 670, "数据与中间件区", 16, color=C["green"], weight="bold")
    ds = [("MySQL 8", "业务数据库"), ("Redis", "缓存 / 令牌"), ("RabbitMQ", "异步消息"), ("Milvus", "向量索引"), ("Nacos", "注册配置中心")]
    for i, (n, sub) in enumerate(ds):
        x = 250 + i * 260
        s.box(x, 620, 230, 76, n, sub=sub, fill="#fff", stroke=C["green"], size=16, tcolor=C["green"])

    s.rect(40, 770, 1520, 130, "#f5eef8", C["purple"], rx=12, dash="6,4")
    s.text(120, 840, "外部服务区", 16, color=C["purple"], weight="bold")
    ex = [("Embedding API", "bge-m3"), ("DeepSeek", "大模型"), ("天气数据", "航线/港口"), ("制裁名单", "合规数据")]
    for i, (n, sub) in enumerate(ex):
        x = 280 + i * 300
        s.box(x, 800, 260, 70, n, sub=sub, fill="#fff", stroke=C["purple"], size=15, tcolor=C["purple"])
    return s.save("fig4-4")


# ---------------------------------------------------------------- 图4-5 总体模块结构
def tree_module(name, root, groups):
    """groups: list of (title, [children...])"""
    n = len(groups)
    width = max(1600, n * 300)
    maxkids = max(len(g[1]) for g in groups)
    height = 220 + maxkids * 66 + 80
    s = SVG(width, height)
    s.text(width / 2, 52, root, 24, color=C["primary"], weight="bold")
    cols = [width * (i + 0.5) / n for i in range(n)]
    s.rect(width / 2 - 130, 80, 260, 56, C["primary"], C["primary"], rx=10)
    s.text(width / 2, 116, name, 17, color="#fff", weight="bold")
    bus_y = 190
    s.line(width / 2, 136, width / 2, bus_y, C["line"], 1.8, marker=False)
    s.line(cols[0], bus_y, cols[-1], bus_y, C["line"], 1.8, marker=False)
    for i, (title, kids) in enumerate(groups):
        x = cols[i]
        s.line(x, bus_y, x, bus_y + 34, C["line"], 1.8, marker=False)
        s.rect(x - 110, bus_y + 34, 220, 50, C["blue_l"], C["blue"], rx=8)
        s.text(x, bus_y + 65, title, 15, color=C["blue"], weight="bold")
        for j, k in enumerate(kids):
            y = bus_y + 118 + j * 62
            s.line(x, bus_y + 84 if j == 0 else y - 62 + 50, x, y, C["line"], 1.2, marker=False)
            s.rect(x - 96, y, 192, 46, "#fff", C["gray"], rx=6, sw=1.1)
            s.text(x, y + 29, k, 13, color=C["ink"])
    return s


def fig_4_5():
    s = tree_module(
        "VANPINE SRM", "全球供应商管理系统总体模块结构",
        [
            ("平台治理子系统", ["认证与登录", "租户管理", "用户管理", "角色与权限", "菜单管理", "入驻审核", "审计日志"]),
            ("智能撮合子系统", ["企业画像", "尽职调查", "制裁名单筛查", "采购需求", "智能匹配", "供需对接"]),
            ("采购交易子系统", ["询盘管理", "询盘邀请", "在线报价", "比价分析", "授标管理", "合同管理"]),
            ("交付履约子系统", ["发货管理", "收货确认", "验收管理", "物流跟踪", "风险预警", "应急处置"]),
            ("AI 智能服务", ["AI 助手对话", "知识库 RAG", "匹配理由生成", "报价分析", "授标理由", "运输天气建议"]),
        ],
    )
    s.text(s.w / 2, s.h - 24, "图4-5  系统总体模块结构图", 15, color=C["gray"])
    return s.save("fig4-5")


def fig_4_6():
    s = tree_module(
        "平台治理", "平台治理子系统模块结构",
        [
            ("身份与访问", ["登录认证", "JWT 令牌", "密码找回", "AI 护照"]),
            ("租户管理", ["入驻申请", "资质审核", "租户开通", "订阅到期"]),
            ("用户管理", ["用户列表", "创建/停用", "个人信息", "修改密码"]),
            ("权限管理", ["角色定义", "菜单授权", "按钮权限", "数据权限"]),
            ("平台治理", ["制裁名单", "审计日志", "消息通知", "文件服务"]),
        ],
    )
    s.text(s.w / 2, s.h - 24, "图4-6  平台治理子系统模块结构图", 15, color=C["gray"])
    return s.save("fig4-6")


# ---------------------------------------------------------------- 图4-7 匹配活动图
def fig_4_7():
    s = SVG(1180, 1320)
    s.text(590, 44, "供应商智能匹配活动图", 22, color=C["primary"], weight="bold")
    cx = 590
    y = 95
    def node(text, h=56, w=330, fill=None, stroke=None, sub=None, size=15):
        nonlocal y
        s.box(cx - w / 2, y, w, h, text, sub=sub, fill=fill or C["blue_l"], stroke=stroke or C["blue"], size=size)
        y += h
    def down(label=None, dash=None):
        nonlocal y
        s.arrow(cx, y, cx, y + 36, label, color=C["line"], dash=dash, lsize=13)
        y += 36
    def decision(text, yes="是", no="否"):
        nonlocal y
        s.diamond(cx, y + 45, 250, 90, text, 14)
        y += 90
    # start
    s.add(f'<circle cx="{cx}" cy="{y+10}" r="10" fill="{C["primary"]}"/>')
    y += 34
    down()
    node("采购需求发布 / 更新")
    down()
    node("需求文本清洗与语义解析", sub="品类、规格、数量、地域、资质")
    down()
    node("调用 Embedding API 生成需求向量")
    down()
    node("Milvus 向量召回候选供应商", fill=C["green_l"], stroke=C["green"])
    down()
    node("合并规则候选池（同品类 / 同地区）")
    down()
    decision("候选是否为空？")
    # no branch loops back to rule candidate pool
    s.path(f"M {cx-125} {y-45} H 265 V {y-155} H {cx-165}", color=C["orange"], marker=True)
    s.text(238, y - 105, "否：扩大品类/地域范围", 13, color=C["orange"], anchor="end")
    down("是")
    node("过滤制裁名单命中与黑名单企业")
    down()
    node("多维度加权评分", sub="权重：品类30/资质25/产品15/地域10/行业5/完整度10/出口5", size=14)
    down()
    node("生成可读匹配理由", sub="规则模板 + 大模型润色")
    down()
    node("按得分排序返回前端展示", fill=C["green_l"], stroke=C["green"])
    down()
    node("采购员查看并选择供应商发起对接")
    down()
    s.add(f'<circle cx="{cx}" cy="{y+18}" r="14" fill="#fff" stroke="{C["primary"]}" stroke-width="2"/>')
    s.add(f'<circle cx="{cx}" cy="{y+18}" r="8" fill="{C["primary"]}"/>')
    s.text(590, 1296, "图4-7  供应商智能匹配活动图", 15, color=C["gray"])
    return s.save("fig4-7")


# ---------------------------------------------------------------- 图4-8 时序图
def fig_4_8():
    s = SVG(1680, 1240)
    s.text(840, 44, "询盘—报价—比价—授标时序图", 22, color=C["primary"], weight="bold")
    lives = [("采购员", 110), ("后台前端", 330), ("API 网关", 550), ("交易服务", 790), ("RabbitMQ", 1010), ("供应商", 1230), ("AI 服务", 1460)]
    top = 90
    for name, x in lives:
        s.rect(x - 75, top, 150, 46, C["blue_l"], C["blue"], rx=8)
        s.text(x, top + 29, name, 15, color=C["blue"], weight="bold")
        s.line(x, top + 46, x, 1150, "#c9d3df", 1.2, dash="5,4", marker=False)
    def msg(a, b, y, text, color=None, dash=None):
        x1, x2 = dict(lives)[a], dict(lives)[b]
        direction = 1 if x2 > x1 else -1
        s.arrow(x1, y, x2 - direction * 4, y, color=color or C["line"], dash=dash, marker=True)
        s.text((x1 + x2) / 2, y - 8, text, 13, color=color or C["ink"])
        # activation
        s.rect(min(x1, x2) + 4, y - 5, abs(x2 - x1) - 8, 10, "#fff", "none", rx=0, sw=0)
    ys = [155, 205, 255, 305, 355, 405, 455, 505, 555, 610, 665, 720, 780, 840, 900, 960, 1020, 1080, 1130]
    msg("采购员", "后台前端", ys[0], "1. 填写询盘并选择目标供应商")
    msg("后台前端", "API 网关", ys[1], "2. POST /api/admin/procurement/inquiry")
    msg("API 网关", "交易服务", ys[2], "3. JWT 校验后转发（携带 tenant_id）")
    msg("交易服务", "RabbitMQ", ys[3], "4. 发布询盘通知事件")
    msg("RabbitMQ", "供应商", ys[4], "5. 站内通知 / 待办提醒")
    msg("供应商", "后台前端", ys[5], "6. 查看询盘详情", color=C["green"])
    msg("后台前端", "API 网关", ys[6], "7. 提交报价", color=C["green"])
    msg("API 网关", "交易服务", ys[7], "8. 保存报价并校验截止时间", color=C["green"])
    msg("采购员", "后台前端", ys[8], "9. 进入比价页面")
    msg("后台前端", "AI 服务", ys[9], "10. 请求报价综合分析")
    msg("AI 服务", "交易服务", ys[10], "11. Feign 拉取询盘与报价明细", color=C["purple"], dash="5,4")
    msg("交易服务", "AI 服务", ys[11], "12. 返回结构化报价数据", color=C["purple"], dash="5,4")
    msg("AI 服务", "后台前端", ys[12], "13. 返回排名、风险点与授标建议", color=C["purple"], dash="5,4")
    msg("采购员", "后台前端", ys[13], "14. 确认授标对象")
    msg("后台前端", "交易服务", ys[14], "15. 创建授标记录")
    msg("交易服务", "AI 服务", ys[15], "16. 生成授标理由")
    msg("AI 服务", "交易服务", ys[16], "17. 返回授标说明", color=C["purple"], dash="5,4")
    msg("交易服务", "RabbitMQ", ys[17], "18. 通知中标 / 未中标供应商")
    msg("RabbitMQ", "供应商", ys[18], "19. 推送授标结果", color=C["green"])
    s.text(840, 1200, "图4-8  询盘到授标核心时序图", 15, color=C["gray"])
    return s.save("fig4-8")


# ---------------------------------------------------------------- 图4-9 交付活动图
def fig_4_9():
    s = SVG(1500, 900)
    s.text(750, 44, "交付履约活动图", 22, color=C["primary"], weight="bold")
    # horizontal flow with decision
    def box(x, y, w, h, t, sub=None, fill=None, stroke=None):
        s.box(x, y, w, h, t, sub=sub, fill=fill or C["blue_l"], stroke=stroke or C["blue"], size=14)
    box(70, 150, 180, 60, "合同生效")
    box(300, 150, 200, 60, "生成分批交付计划")
    box(550, 150, 180, 60, "供应商发货")
    box(780, 150, 200, 60, "物流事件回传")
    box(1030, 150, 190, 60, "买家收货确认")
    box(1270, 150, 180, 60, "到货验收")
    for x in [250, 500, 730, 980, 1220]:
        s.arrow(x, 180, x + 50, 180, color=C["primary"])
    # decision
    s.diamond(1360, 310, 260, 100, "验收是否合格？", 14)
    s.line(1360, 210, 1360, 260, color=C["primary"], marker=True)
    # yes
    box(1270, 400, 180, 60, "确认入库", fill=C["green_l"], stroke=C["green"])
    s.arrow(1360, 360, 1360, 400, "是", color=C["green"])
    # no
    box(1010, 400, 220, 60, "退换货 / 部分拒收", fill=C["red_l"], stroke=C["red"])
    s.arrow(1230, 310, 1230, 400, "否", color=C["red"])
    # risk scan loop
    box(620, 400, 260, 60, "风险扫描与分级预警", fill=C["orange_l"], stroke=C["orange"])
    s.arrow(1100, 430, 880, 430, color=C["orange"])
    box(300, 400, 260, 60, "应急处置：备选供应商/替代航线", fill=C["orange_l"], stroke=C["orange"])
    s.arrow(620, 430, 560, 430, color=C["orange"])
    # bottom lane labels
    s.rect(60, 540, 1390, 120, "#f8fafc", C["gray"], rx=10, dash="6,4")
    s.text(120, 600, "履约保障机制", 15, color=C["gray"], weight="bold")
    items = ["承诺交期管理", "物流轨迹存证", "8D 质量报告", "违约罚金计算", "补救工单闭环"]
    for i, t in enumerate(items):
        x = 330 + i * 220
        s.rect(x, 570, 190, 48, "#fff", C["gray"], rx=6, sw=1)
        s.text(x + 95, 600, t, 13, color=C["ink"])
    s.text(750, 730, "交付不是终点：每个节点自动存证，风险事件自动关联订单并触发处置", 15, color=C["gray"])
    s.text(750, 850, "图4-9  交付履约活动图", 15, color=C["gray"])
    return s.save("fig4-9")


# ---------------------------------------------------------------- E-R 图
def entity_box(s, cx, cy, name, w=210, h=70, fill=None):
    s.rect(cx - w / 2, cy - h / 2, w, h, fill or C["blue_l"], C["blue"], rx=4, sw=1.8)
    s.text(cx, cy + 6, name, 18, color=C["primary"], weight="bold")


def attr(s, cx, cy, name, rx=None, fill=None, size=14):
    rx = rx or max(58, len(name) * 10 + 18)
    s.ellipse(cx, cy, rx, 26, name, fill or "#fff", C["gray"], size)


def rect_edge(cx, cy, w, h, tx, ty):
    dx, dy = tx - cx, ty - cy
    if dx == 0 and dy == 0:
        return cx, cy
    sx = (w / 2) / abs(dx) if dx else 1e9
    sy = (h / 2) / abs(dy) if dy else 1e9
    t = min(sx, sy)
    return cx + dx * t, cy + dy * t


def fig_er_attr_1():
    s = SVG(1300, 760)
    s.text(650, 44, "租户实体属性图", 22, color=C["primary"], weight="bold")
    entity_box(s, 650, 380, "租户", 220, 80)
    attrs = [("租户编号", 180, 180), ("租户编码", 400, 110), ("企业名称", 650, 95),
             ("所在国家", 900, 110), ("租户状态", 1120, 180), ("订阅到期", 1100, 600),
             ("联系人", 850, 670), ("联系电话", 600, 690), ("企业注册号", 350, 640), ("创建时间", 180, 540)]
    for n, x, y in attrs:
        ex, ey = rect_edge(650, 380, 220, 80, x, y)
        s.line(ex, ey, x, y, C["line"], 1.2, marker=False)
        attr(s, x, y, n)
    s.text(650, 730, "（a）租户实体属性")
    return s.save("fig4-10a")


def fig_er_attr_2():
    s = SVG(1300, 760)
    s.text(650, 44, "用户实体属性图", 22, color=C["primary"], weight="bold")
    entity_box(s, 650, 380, "用户", 220, 80)
    attrs = [("用户编号", 180, 180), ("用户名", 400, 110), ("密码哈希", 650, 95),
             ("真实姓名", 900, 110), ("电子邮箱", 1120, 180), ("手机号码", 1100, 600),
             ("账号状态", 850, 670), ("最后登录IP", 600, 690), ("所属租户", 350, 640), ("创建时间", 180, 540)]
    for n, x, y in attrs:
        ex, ey = rect_edge(650, 380, 220, 80, x, y)
        s.line(ex, ey, x, y, C["line"], 1.2, marker=False)
        attr(s, x, y, n)
    s.text(650, 730, "（b）用户实体属性")
    return s.save("fig4-10b")


def fig_4_11():
    s = SVG(1300, 760)
    s.text(650, 44, "采购需求实体属性图", 22, color=C["primary"], weight="bold")
    entity_box(s, 650, 380, "采购需求", 230, 80)
    attrs = [("需求编号", 170, 180), ("需求标题", 400, 105), ("采购品类", 650, 95),
             ("数量", 900, 105), ("计量单位", 1130, 180), ("目标地区", 1120, 600),
             ("预算金额", 870, 675), ("截止日期", 620, 690), ("需求状态", 370, 640), ("是否公开", 170, 540)]
    for n, x, y in attrs:
        s.line(650, 380, x, y, C["line"], 1.2, marker=False)
        attr(s, x, y, n)
    s.text(650, 730, "图4-11  采购需求实体属性图")
    return s.save("fig4-11")


def rel_line(s, a, b, label_left="1", label_right="n"):
    s.line(a[0], a[1], b[0], b[1], C["line"], 1.4, marker=False)
    mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    s.text(mx - 12, my - 8, label_left, 14, color=C["orange"], weight="bold")
    s.text(mx + 12, my - 8, label_right, 14, color=C["orange"], weight="bold")


def fig_4_12():
    s = SVG(1500, 900)
    s.text(750, 44, "局部 E-R 图（租户与权限）", 22, color=C["primary"], weight="bold")
    ent = {
        "tenant": (240, 440), "user": (620, 440), "role": (990, 440), "menu": (1330, 280), "permission": (1330, 620),
    }
    for k, (x, y) in ent.items():
        entity_box(s, x, y, {"tenant": "租户", "user": "用户", "role": "角色", "menu": "菜单", "permission": "权限"}[k], 170, 66)
    # tenant 1:n user
    s.line(325, 440, 535, 440, C["line"], 1.4)
    s.text(370, 428, "1", 14, color=C["orange"], weight="bold")
    s.text(490, 428, "n", 14, color=C["orange"], weight="bold")
    s.text(430, 480, "拥有")
    # user m:n role
    s.diamond(805, 440, 120, 70, "用户角色")
    s.line(705, 440, 745, 440, C["line"], 1.4)
    s.line(865, 440, 905, 440, C["line"], 1.4)
    s.text(730, 426, "m", 14, color=C["orange"], weight="bold")
    s.text(880, 426, "n", 14, color=C["orange"], weight="bold")
    # role m:n menu
    s.line(1075, 425, 1230, 300, C["line"], 1.4)
    s.diamond(1160, 360, 120, 70, "角色菜单")
    s.text(1110, 360, "m", 14, color=C["orange"], weight="bold")
    s.text(1210, 350, "n", 14, color=C["orange"], weight="bold")
    # role m:n permission
    s.line(1075, 455, 1230, 600, C["line"], 1.4)
    s.diamond(1160, 530, 120, 70, "角色权限")
    s.text(1110, 530, "m", 14, color=C["orange"], weight="bold")
    s.text(1210, 540, "n", 14, color=C["orange"], weight="bold")
    s.text(750, 840, "图4-12  租户与权限局部 E-R 图", 15, color=C["gray"])
    return s.save("fig4-12")


def relation(s, p1, p2, label, l="1", r="n", dw=96, dh=46, w=160, h=62):
    x1, y1 = p1
    x2, y2 = p2
    dx, dy = x2 - x1, y2 - y1
    ln = max(1.0, (dx * dx + dy * dy) ** 0.5)
    ux, uy = dx / ln, dy / ln
    # 从实体矩形边界出发，避免线条压住实体名称
    ax, ay = rect_edge(x1, y1, w, h, x2, y2)
    bx, by = rect_edge(x2, y2, w, h, x1, y1)
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    s.line(ax, ay, mx - dw / 2, my, C["line"], 1.4)
    s.line(mx + dw / 2, my, bx, by, C["line"], 1.4)
    s.diamond(mx, my, dw, dh, label, 12)
    if abs(uy) < 0.3:
        ox, oy = 0, -14
    elif abs(ux) < 0.3:
        ox, oy = 14, 0
    else:
        ox, oy = 12, -12
    s.text(ax + ux * 20 + ox, ay + uy * 20 + oy + 4, l, 13, color=C["primary"], weight="bold")
    s.text(bx - ux * 20 + ox, by - uy * 20 + oy + 4, r, 13, color=C["primary"], weight="bold")


def fig_4_13():
    s = SVG(1400, 760)
    s.text(700, 46, "局部 E-R 图（采购交易）", 22, color=C["primary"], weight="bold")
    E = {
        "租户": (130, 330), "采购需求": (390, 330), "对接记录": (390, 130), "询盘": (680, 330),
        "询盘邀请": (680, 130), "报价": (940, 330), "授标": (1200, 330), "合同": (940, 560),
    }
    for name, (x, y) in E.items():
        entity_box(s, x, y, name, 160, 62)
    relation(s, E["租户"], E["采购需求"], "发布", "1", "n", dw=80, dh=40)
    relation(s, E["采购需求"], E["对接记录"], "产生", "1", "n", dw=80, dh=40)
    relation(s, E["采购需求"], E["询盘"], "发起", "1", "n", dw=80, dh=40)
    relation(s, E["询盘"], E["询盘邀请"], "邀请", "1", "n", dw=80, dh=40)
    relation(s, E["询盘"], E["报价"], "收到", "1", "n", dw=80, dh=40)
    relation(s, E["报价"], E["授标"], "中标", "1", "1", dw=80, dh=40)
    relation(s, E["询盘"], E["合同"], "签约", "1", "1", dw=80, dh=40)
    s.text(700, 690, "图4-13  采购交易局部 E-R 图", 15, color=C["gray"])
    return s.save("fig4-13")


def fig_4_14():
    s = SVG(1350, 760)
    s.text(675, 46, "局部 E-R 图（合同与交付）", 22, color=C["primary"], weight="bold")
    E = {
        "物流事件": (700, 130), "8D报告": (1100, 130), "合同": (130, 380),
        "交付单": (420, 380), "交付风险": (720, 380), "补救动作": (1040, 380), "交付存证": (420, 610),
    }
    for name, (x, y) in E.items():
        entity_box(s, x, y, name, 165, 62)
    relation(s, E["合同"], E["交付单"], "分批交付", "1", "n", dw=90, dh=42)
    relation(s, E["交付单"], E["物流事件"], "记录", "1", "n", dw=82, dh=40)
    relation(s, E["交付单"], E["交付风险"], "产生", "1", "n", dw=82, dh=40)
    relation(s, E["交付风险"], E["补救动作"], "触发", "1", "n", dw=82, dh=40)
    relation(s, E["交付单"], E["交付存证"], "存证", "1", "n", dw=82, dh=40)
    relation(s, E["交付单"], E["8D报告"], "质量分析", "1", "n", dw=92, dh=42)
    s.text(675, 700, "图4-14  合同与交付局部 E-R 图", 15, color=C["gray"])
    return s.save("fig4-14")


def fig_4_15():
    s = SVG(1800, 1300)
    s.text(900, 44, "系统汇总 E-R 图", 24, color=C["primary"], weight="bold")
    E = {
        "入驻申请": (220, 180), "租户": (520, 180), "用户": (820, 180), "角色": (1120, 180), "菜单": (1420, 180),
        "企业画像": (520, 400), "尽职调查": (220, 400), "制裁记录": (220, 620), "核查项": (520, 620),
        "采购需求": (820, 400), "对接记录": (820, 620), "询盘": (1120, 400),
        "询盘邀请": (1420, 400), "报价": (1120, 620), "授标": (1420, 620),
        "合同": (1120, 850), "交付单": (820, 850), "物流事件": (520, 850), "交付风险": (820, 1080), "补救动作": (1120, 1080),
    }
    for name, (x, y) in E.items():
        entity_box(s, x, y, name, 170, 62)
    rels = [
        ("入驻申请", "租户", "审核开通", "1", "1"), ("租户", "用户", "拥有", "1", "n"),
        ("用户", "角色", "分配", "m", "n"), ("角色", "菜单", "授权", "m", "n"),
        ("租户", "企业画像", "维护", "1", "1"), ("入驻申请", "尽职调查", "发起", "1", "1"),
        ("尽职调查", "核查项", "包含", "1", "n"), ("入驻申请", "制裁记录", "筛查", "1", "n"),
        ("租户", "采购需求", "发布", "1", "n"), ("采购需求", "对接记录", "产生", "1", "n"),
        ("采购需求", "询盘", "发起", "1", "n"), ("询盘", "询盘邀请", "邀请", "1", "n"),
        ("询盘", "报价", "收到", "1", "n"), ("报价", "授标", "中标", "1", "1"),
        ("询盘", "合同", "签约", "1", "1"), ("合同", "交付单", "分批交付", "1", "n"),
        ("交付单", "物流事件", "记录", "1", "n"), ("交付单", "交付风险", "产生", "1", "n"),
        ("交付风险", "补救动作", "触发", "1", "n"),
    ]
    for a, b, lab, l, r in rels:
        relation(s, E[a], E[b], lab, l, r, dw=82, dh=40, w=170)
    s.text(900, 1220, "图4-15  系统汇总 E-R 图（消除冲突后）", 15, color=C["gray"])
    return s.save("fig4-15")


if __name__ == "__main__":
    files = []
    for fn in [fig_3_1, fig_3_2, fig_3_3, fig_3_4, fig_3_5,
               fig_4_1, fig_4_2, fig_4_3, fig_4_4, fig_4_5, fig_4_6,
               fig_4_7, fig_4_8, fig_4_9,
               fig_er_attr_1, fig_er_attr_2, fig_4_11, fig_4_12, fig_4_13, fig_4_14, fig_4_15]:
        files.append(fn())
    print("\n".join(files))
