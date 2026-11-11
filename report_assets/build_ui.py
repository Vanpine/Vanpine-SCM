# -*- coding: utf-8 -*-
"""生成第 5 章系统界面效果图（HTML -> PNG）。"""
import os, subprocess

BASE = os.path.dirname(__file__)
UI = os.path.join(BASE, "ui")
os.makedirs(UI, exist_ok=True)
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{width:1600px;height:1000px;overflow:hidden;font-family:'Noto Sans SC','PingFang SC','Microsoft YaHei',sans-serif;
color:#334155;background:#f5f6f8;letter-spacing:.4px;font-size:14px}
.app{display:flex;width:1600px;height:1000px}
.sidebar{width:220px;flex:0 0 220px;background:linear-gradient(180deg,#0d1b2a 0%,#0a1525 100%);color:#fff;padding:18px 12px;display:flex;flex-direction:column}
.brand{display:flex;align-items:center;gap:10px;padding:4px 10px 22px}
.brand .mark{width:34px;height:34px;border-radius:9px;background:linear-gradient(135deg,#0066ff,#00b4ff);display:grid;place-items:center;font-weight:800;font-size:15px}
.brand .name{font-size:17px;font-weight:700;letter-spacing:.5px}
.brand .sub{font-size:10px;color:rgba(255,255,255,.45);margin-top:2px;letter-spacing:1px}
.menu{flex:1;overflow:hidden}
.mgroup{font-size:11px;color:rgba(255,255,255,.3);margin:16px 12px 6px;letter-spacing:1.5px}
.mitem{display:flex;align-items:center;gap:10px;height:38px;padding:0 12px;border-radius:8px;color:rgba(255,255,255,.62);font-size:13.5px;margin-bottom:2px}
.mitem svg{width:16px;height:16px;stroke:currentColor;fill:none;stroke-width:1.7;opacity:.9}
.mitem.active{background:linear-gradient(135deg,rgba(0,102,255,.38),rgba(0,160,255,.18));color:#fff;box-shadow:inset 0 0 0 1px rgba(0,180,255,.18)}
.mitem.active svg{stroke:#00b4ff}
.main{flex:1;display:flex;flex-direction:column;min-width:0}
.header{height:62px;background:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 26px;box-shadow:0 1px 3px rgba(15,23,42,.06);z-index:2}
.crumb{font-size:15px;color:#64748b}
.crumb b{color:#1a1a2e;font-weight:600}
.hactions{display:flex;align-items:center;gap:18px;color:#94a3b8}
.hicon{width:34px;height:34px;border-radius:9px;background:#f1f5f9;display:grid;place-items:center;position:relative}
.hicon svg{width:17px;height:17px;stroke:#64748b;fill:none;stroke-width:1.7}
.dot{position:absolute;right:7px;top:7px;width:7px;height:7px;border-radius:50%;background:#f56c6c;border:2px solid #fff}
.user{display:flex;align-items:center;gap:9px;color:#334155;font-size:13.5px}
.avatar{width:34px;height:34px;border-radius:50%;background:linear-gradient(135deg,#0066ff,#00b4ff);color:#fff;display:grid;place-items:center;font-weight:600;font-size:13px}
.content{flex:1;padding:16px 24px 18px;overflow:hidden}
.page-title{font-size:20px;font-weight:700;color:#1a1a2e;margin-bottom:4px}
.page-sub{font-size:12.5px;color:#94a3b8;margin-bottom:16px}
.card{background:#fff;border-radius:12px;box-shadow:0 1px 3px rgba(15,23,42,.06)}
.row{display:flex;gap:16px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:16px}
.stat{padding:18px 20px;position:relative;overflow:hidden}
.stat .lab{font-size:13px;color:#64748b}
.stat .num{font-size:30px;font-weight:700;color:#1a1a2e;margin-top:8px;font-family:'DIN Alternate','PingFang SC',sans-serif}
.stat .trend{font-size:11.5px;color:#16a34a;margin-top:6px}
.stat .trend.down{color:#dc2626}
.stat .ico{position:absolute;right:18px;top:18px;width:36px;height:36px;border-radius:10px;display:grid;place-items:center}
.stat .ico svg{width:19px;height:19px;fill:none;stroke:#0066ff;stroke-width:1.8}
.btn{display:inline-flex;align-items:center;gap:6px;height:34px;padding:0 16px;border-radius:8px;font-size:13px}
.btn.primary{background:#0066ff;color:#fff;box-shadow:0 4px 10px rgba(0,102,255,.22)}
.btn.plain{background:#fff;color:#475569;border:1px solid #e2e8f0}
.btn.ghost{background:#f1f5f9;color:#475569}
.tag{display:inline-flex;align-items:center;height:23px;padding:0 9px;border-radius:5px;font-size:11.5px;font-weight:500}
.tag.green{background:#e8f7ee;color:#15803d}.tag.blue{background:#e8f1ff;color:#1d4ed8}
.tag.orange{background:#fff3e6;color:#c2410c}.tag.red{background:#feecec;color:#b91c1c}
.tag.gray{background:#f1f5f9;color:#64748b}.tag.purple{background:#f2ebff;color:#6d28d9}
table{width:100%;border-collapse:collapse}
th{background:#f8fafc;color:#64748b;font-size:12px;font-weight:600;text-align:left;padding:11px 14px;border-bottom:1px solid #eef2f7;white-space:nowrap}
td{font-size:13px;padding:12px 14px;border-bottom:1px solid #f1f5f9;color:#334155;white-space:nowrap}
tr:last-child td{border-bottom:none}
.link{color:#0066ff}
.muted{color:#94a3b8;font-size:12px}
.field{display:flex;flex-direction:column;gap:7px}
.field label{font-size:12.5px;color:#64748b}
.input{height:38px;border:1px solid #e2e8f0;border-radius:8px;display:flex;align-items:center;padding:0 12px;color:#334155;background:#fff;font-size:13.5px}
.input.filled{color:#1e293b}
.select::after{content:'⌄';margin-left:auto;color:#94a3b8}
.ring{width:116px;height:116px;border-radius:50%;background:conic-gradient(#0066ff 0 86%,#eef2f7 86% 100%);display:grid;place-items:center;position:relative}
.ring::after{content:'';position:absolute;inset:9px;border-radius:50%;background:#fff}
.ring span{position:relative;z-index:1;font-size:26px;font-weight:700;color:#0066ff}
.ring span small{font-size:12px;color:#94a3b8;font-weight:500}
.pbar{height:7px;border-radius:6px;background:#eef2f7;overflow:hidden}
.pbar i{display:block;height:100%;border-radius:6px;background:linear-gradient(90deg,#0066ff,#00b4ff)}
"""

ICON = {
    "grid": '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>',
    "user": '<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5"/></svg>',
    "users": '<svg viewBox="0 0 24 24"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 3-5.8 6.5-5.8s6.5 2.2 6.5 5.8"/><path d="M16 5.2a3.5 3.5 0 0 1 0 6.6M17.5 14.5c2.4.5 4 2.3 4 5"/></svg>',
    "doc": '<svg viewBox="0 0 24 24"><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M9 12h6M9 16h6"/></svg>',
    "send": '<svg viewBox="0 0 24 24"><path d="M21 3 10 14"/><path d="M21 3 14 21l-4-7-7-4z"/></svg>',
    "compare": '<svg viewBox="0 0 24 24"><path d="M4 20V9M10 20V4M16 20v-7M22 20H2"/></svg>',
    "contract": '<svg viewBox="0 0 24 24"><path d="M5 3h9l5 5v13H5z"/><path d="M14 3v5h5M8 13h8M8 17h5"/></svg>',
    "truck": '<svg viewBox="0 0 24 24"><path d="M3 6h10v10H3zM13 10h4l4 3v3h-8z"/><circle cx="7" cy="18" r="2"/><circle cx="17.5" cy="18" r="2"/></svg>',
    "box": '<svg viewBox="0 0 24 24"><path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z"/><path d="M3 7.5 12 12l9-4.5M12 12v9"/></svg>',
    "check": '<svg viewBox="0 0 24 24"><path d="m4 12 5 5L20 6"/></svg>',
    "warn": '<svg viewBox="0 0 24 24"><path d="M12 3 2.5 20h19z"/><path d="M12 9v5M12 17.2v.3"/></svg>',
    "shield": '<svg viewBox="0 0 24 24"><path d="M12 3 4 6v6c0 5 3.4 8 8 9 4.6-1 8-4 8-9V6z"/><path d="m8.5 12 2.5 2.5 4.5-5"/></svg>',
    "bot": '<svg viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3"/><path d="M12 4v4M8 14h.01M16 14h.01M9 18h6"/></svg>',
    "gear": '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4v-.2a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H3v-4h.2a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6V3h4v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1z"/></svg>',
    "search": '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>',
    "bell": '<svg viewBox="0 0 24 24"><path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>',
    "globe": '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.5 3 14 0 18M12 3c-3 3.5-3 14 0 18"/></svg>',
    "clock": '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/></svg>',
    "chart": '<svg viewBox="0 0 24 24"><path d="M4 20V5M4 20h16"/><path d="m7 15 4-4 3 3 5-6"/></svg>',
}

SIDEBAR = [
    ("", [("工作台", "grid", True)]),
    ("智能撮合", [("企业画像", "user"), ("采购需求", "doc"), ("对接记录", "users"), ("找供应商", "search")]),
    ("采购管理", [("询盘管理", "send"), ("比价管理", "compare"), ("合同管理", "contract")]),
    ("可信交付", [("发货管理", "truck"), ("收货确认", "box"), ("验收管理", "check"), ("风险监控", "warn")]),
    ("", [("尽职调查", "shield"), ("AI 知识库", "bot")]),
    ("系统管理", [("租户管理", "globe"), ("用户管理", "users"), ("入驻预审", "doc"), ("操作日志", "clock"), ("制裁名单", "shield")]),
]


def sidebar(active):
    out = ['<aside class="sidebar"><div class="brand"><div class="mark">V</div><div><div class="name">Vanpine SRM</div><div class="sub">GLOBAL SUPPLIER NETWORK</div></div></div><nav class="menu">']
    for group, items in SIDEBAR:
        if group:
            out.append(f'<div class="mgroup">{group}</div>')
        for label, icon, *act in items:
            cls = "mitem active" if label == active else "mitem"
            out.append(f'<div class="{cls}">{ICON[icon]}<span>{label}</span></div>')
    out.append('</nav></aside>')
    return "".join(out)


def header(crumb):
    return f'''<header class="header">
      <div class="crumb">首页 / <b>{crumb}</b></div>
      <div class="hactions">
        <div class="hicon">{ICON["search"]}</div>
        <div class="hicon">{ICON["bell"]}<i class="dot"></i></div>
        <div class="hicon">{ICON["globe"]}</div>
        <div class="user"><div class="avatar">张</div><div>张经理<br><span class="muted" style="font-size:11px">采购企业 / 管理员</span></div></div>
      </div>
    </header>'''


def frame(active, crumb, body):
    return f'''<!DOCTYPE html><html><head><meta charset="utf-8"><style>{CSS}</style></head>
<body><div class="app">{sidebar(active)}<div class="main">{header(crumb)}<div class="content">{body}</div></div></div></body></html>'''


def save(name, html):
    p = os.path.join(UI, name + ".html")
    open(p, "w", encoding="utf8").write(html)
    png = os.path.join(UI, name + ".png")
    subprocess.run([CHROME, "--headless", "--disable-gpu", "--hide-scrollbars",
                    "--force-device-scale-factor=2", "--window-size=1600,1000",
                    f"--screenshot={png}", "file://" + p],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return png


# ---------------------------------------------------------------- 登录页
def ui_login():
    html = f'''<!DOCTYPE html><html><head><meta charset="utf-8"><style>{CSS}
    body{{background:#fff}}
    .login{{display:flex;width:1600px;height:1000px}}
    .left{{width:900px;position:relative;background:
      radial-gradient(900px 600px at 15% 8%,rgba(0,180,255,.25),transparent 60%),
      radial-gradient(700px 500px at 90% 90%,rgba(0,102,255,.22),transparent 60%),
      linear-gradient(135deg,#071426 0%,#0b1f3a 55%,#0a2a4d 100%);
      color:#fff;padding:64px 72px;display:flex;flex-direction:column;justify-content:space-between;overflow:hidden}}
    .left::after{{content:'';position:absolute;right:-160px;top:120px;width:520px;height:520px;border-radius:50%;
      border:1px solid rgba(120,200,255,.18)}}
    .left::before{{content:'';position:absolute;right:-40px;bottom:-120px;width:360px;height:360px;border-radius:50%;
      border:1px solid rgba(120,200,255,.12)}}
    .lg-brand{{display:flex;align-items:center;gap:14px;font-size:22px;font-weight:700}}
    .lg-brand .mark{{width:46px;height:46px;border-radius:12px;background:linear-gradient(135deg,#0066ff,#00b4ff);display:grid;place-items:center;font-size:20px;font-weight:800}}
    .lg-main{{position:relative;z-index:2;max-width:620px}}
    .lg-main h1{{font-size:48px;line-height:1.25;letter-spacing:1px;font-weight:800}}
    .lg-main h1 span{{background:linear-gradient(90deg,#4fc3ff,#9ce6ff);-webkit-background-clip:text;color:transparent}}
    .lg-main p{{margin-top:22px;font-size:16px;line-height:1.9;color:rgba(255,255,255,.62);max-width:520px}}
    .feats{{display:flex;gap:14px;margin-top:38px}}
    .feat{{padding:14px 18px;border-radius:12px;background:rgba(255,255,255,.06);border:1px solid rgba(120,200,255,.14);font-size:13.5px;color:rgba(255,255,255,.82)}}
    .feat b{{display:block;color:#7fd4ff;font-size:15px;margin-bottom:4px}}
    .lg-foot{{font-size:12px;color:rgba(255,255,255,.35)}}
    .right{{flex:1;display:flex;align-items:center;justify-content:center;background:#f7f9fc;position:relative}}
    .login-card{{width:400px;background:#fff;border-radius:18px;padding:40px 38px;box-shadow:0 24px 60px rgba(15,23,42,.10)}}
    .login-card h2{{font-size:24px;color:#0f172a}}
    .login-card .sub{{font-size:13px;color:#94a3b8;margin-top:8px;margin-bottom:28px}}
    .lg-field{{margin-bottom:18px}}
    .lg-field label{{display:block;font-size:12.5px;color:#64748b;margin-bottom:8px}}
    .lg-input{{height:44px;border:1px solid #e2e8f0;border-radius:9px;display:flex;align-items:center;gap:10px;padding:0 14px;color:#94a3b8;background:#fff}}
    .lg-input svg{{width:17px;height:17px;stroke:#94a3b8;fill:none;stroke-width:1.7}}
    .lg-input .val{{color:#1e293b;font-size:13.5px}}
    .verify{{height:44px;border:1px solid #e2e8f0;border-radius:9px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;background:#f8fafc;color:#64748b;font-size:13px}}
    .verify .track{{width:180px;height:32px;border-radius:7px;background:#eef2f7;position:relative}}
    .verify .thumb{{position:absolute;left:2px;top:2px;width:28px;height:28px;border-radius:6px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.15);display:grid;place-items:center;color:#0066ff;font-size:13px}}
    .lg-btn{{width:100%;height:46px;border-radius:9px;background:linear-gradient(90deg,#0066ff,#0088ff);color:#fff;display:grid;place-items:center;font-size:15px;font-weight:600;margin-top:6px;box-shadow:0 10px 24px rgba(0,102,255,.28)}}
    .lg-links{{display:flex;justify-content:space-between;font-size:12.5px;color:#94a3b8;margin-top:16px}}
    .lg-links .blue{{color:#0066ff}}
    .tips{{margin-top:26px;padding:12px 14px;border-radius:10px;background:#f1f7ff;color:#4770a8;font-size:12px;line-height:1.7}}
    </style></head><body><div class="login">
      <div class="left">
        <div class="lg-brand"><div class="mark">V</div><div>Vanpine SRM</div></div>
        <div class="lg-main">
          <h1>让全球采购<br><span>更智能、更可信</span></h1>
          <p>从供应商入驻、资质尽调，到智能匹配、询盘比价、电子签约与交付风控，一条链路完成跨境采购协同。</p>
          <div class="feats">
            <div class="feat"><b>AI 智能撮合</b>向量召回 + 多维评分</div>
            <div class="feat"><b>合规尽调</b>制裁名单与股权穿透</div>
            <div class="feat"><b>可信交付</b>物流存证与风险预警</div>
          </div>
        </div>
        <div class="lg-foot">© 2026 Vanpine Technology · 全球供应链 AI 赋能平台</div>
      </div>
      <div class="right">
        <div class="login-card">
          <h2>欢迎登录</h2>
          <div class="sub">全球供应链 AI 赋能平台 · 企业工作台</div>
          <div class="lg-field"><label>企业编码</label>
            <div class="lg-input">{ICON["globe"]}<span class="val">VP-GLOBAL-001</span></div></div>
          <div class="lg-field"><label>登录账号</label>
            <div class="lg-input">{ICON["user"]}<span class="val">zhang.manager</span></div></div>
          <div class="lg-field"><label>登录密码</label>
            <div class="lg-input">{ICON["shield"]}<span class="val">••••••••••</span></div></div>
          <div class="lg-field"><label>安全验证</label>
            <div class="verify"><div class="track"><div class="thumb">→</div></div><span>按住滑块，拖动完成验证</span></div></div>
          <div class="lg-btn">登 录</div>
          <div class="lg-links"><span>忘记密码？</span><span class="blue">注册企业账号 →</span></div>
          <div class="tips">登录即代表同意《平台服务协议》和《隐私政策》。新企业请先提交入驻申请，平台审核通过后开通租户。</div>
        </div>
      </div>
    </div></body></html>'''
    return save("fig5-1", html)


# ---------------------------------------------------------------- 工作台
def ui_dashboard():
    bars = [("6月", 62), ("7月", 78), ("8月", 55), ("9月", 88), ("10月", 72), ("11月", 96)]
    bars_html = "".join(
        f'<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:8px">'
        f'<div style="font-size:11px;color:#94a3b8">¥{v*13}万</div>'
        f'<div style="width:34px;height:{v*1.7}px;border-radius:6px 6px 2px 2px;background:linear-gradient(180deg,#4da3ff,#0066ff)"></div>'
        f'<div style="font-size:11.5px;color:#94a3b8">{m}</div></div>'
        for m, v in bars)
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">工作台</div><div class="page-sub" style="margin:0">2026 年 11 月 06 日 · 星期五 · 全球供应链实时概览</div></div>
      <div style="display:flex;gap:10px"><div class="btn plain">导出月报</div><div class="btn primary">＋ 发布采购需求</div></div>
    </div>
    <div class="card" style="padding:16px 24px;margin-bottom:14px;display:flex;align-items:center;justify-content:space-between;background:linear-gradient(100deg,#eef6ff,#f8fbff)">
      <div><div style="font-size:18px;font-weight:700;color:#0f2a4a">早上好，张经理 👋</div>
      <div style="font-size:13px;color:#64748b;margin-top:7px">今天有 <b style="color:#0066ff">7 家供应商</b> 提交了新报价，<b style="color:#c2410c">3 笔交付</b> 触发风险预警，请及时处理。</div></div>
      <div style="display:flex;gap:10px"><span class="tag blue">最近登录 2026-11-06 08:42</span><span class="tag green">企业认证已通过</span></div>
    </div>
    <div class="stats">
      <div class="card stat"><div class="lab">在途询盘</div><div class="num">18</div><div class="trend">↑ 较上月 +12.5%</div><div class="ico" style="background:#e8f1ff">{ICON["send"].replace("</svg>", "</svg>")}</div></div>
      <div class="card stat"><div class="lab">待归集报价</div><div class="num">7</div><div class="trend">↑ 今日新增 3 份</div><div class="ico" style="background:#fff3e6">{ICON["doc"]}</div></div>
      <div class="card stat"><div class="lab">待确认验收</div><div class="num">5</div><div class="trend down">2 笔临近交期</div><div class="ico" style="background:#e8f7ee">{ICON["check"]}</div></div>
      <div class="card stat"><div class="lab">风险预警</div><div class="num" style="color:#c2410c">3</div><div class="trend down">1 笔高风险需处置</div><div class="ico" style="background:#feecec">{ICON["warn"]}</div></div>
    </div>
    <div class="row" style="margin-bottom:16px">
      <div class="card" style="flex:1;padding:18px 20px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:18px">
          <div><div style="font-size:15px;font-weight:600">近 6 个月采购金额趋势</div><div class="muted" style="margin-top:3px">单位：万元 · 含已签约与在途订单</div></div>
          <div style="display:flex;gap:6px"><span class="tag blue">采购金额</span><span class="tag gray">询盘数量</span></div>
        </div>
        <div style="display:flex;align-items:flex-end;gap:26px;height:150px;padding:0 10px">{bars_html}</div>
      </div>
      <div class="card" style="width:360px;padding:18px 20px">
        <div style="font-size:15px;font-weight:600;margin-bottom:14px">待办事项</div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
          <div style="padding:14px;border-radius:10px;background:#f8fafc"><div class="muted">待审核入驻</div><div style="font-size:24px;font-weight:700;color:#1d4ed8">4</div></div>
          <div style="padding:14px;border-radius:10px;background:#f8fafc"><div class="muted">待确认授标</div><div style="font-size:24px;font-weight:700;color:#c2410c">2</div></div>
          <div style="padding:14px;border-radius:10px;background:#f8fafc"><div class="muted">待签署合同</div><div style="font-size:24px;font-weight:700;color:#6d28d9">3</div></div>
          <div style="padding:14px;border-radius:10px;background:#f8fafc"><div class="muted">待处理风险</div><div style="font-size:24px;font-weight:700;color:#b91c1c">3</div></div>
        </div>
        <div style="margin-top:16px;padding:14px;border-radius:10px;background:#f1f7ff">
          <div style="font-size:12.5px;color:#4770a8;line-height:1.8">AI 建议：本周铜材价格指数上涨 4.2%，建议对 2 笔未锁价合同启动价格复核。</div>
        </div>
      </div>
    </div>
    <div class="card" style="padding:0 0 6px">
      <div style="display:flex;justify-content:space-between;align-items:center;padding:16px 20px 10px">
        <div style="font-size:15px;font-weight:600">最近采购订单</div><div class="link" style="font-size:12.5px">查看全部 →</div>
      </div>
      <table><thead><tr><th>询盘编号</th><th>采购品类</th><th>目标供应商</th><th>报价金额</th><th>交期</th><th>状态</th><th>更新时间</th></tr></thead>
      <tbody>
      <tr><td class="link">INQ-20261103-008</td><td>精密轴承 / 6205-2RS</td><td>宁波恒动机械</td><td>¥ 862,400</td><td>2026-12-18</td><td><span class="tag orange">比价中</span></td><td class="muted">10 分钟前</td></tr>
      <tr><td class="link">INQ-20261102-006</td><td>工业级 PLC 模块</td><td>苏州智控电子</td><td>¥ 1,286,000</td><td>2026-12-05</td><td><span class="tag blue">待授标</span></td><td class="muted">1 小时前</td></tr>
      <tr><td class="link">INQ-20261101-004</td><td>食品级包装膜</td><td>青岛海润包装</td><td>¥ 398,500</td><td>2026-11-28</td><td><span class="tag green">已签约</span></td><td class="muted">昨天 16:20</td></tr>
      <tr><td class="link">INQ-20261030-012</td><td>不锈钢法兰</td><td>温州法兰管件</td><td>¥ 556,800</td><td>2026-11-22</td><td><span class="tag red">交付预警</span></td><td class="muted">昨天 09:05</td></tr>
      </tbody></table>
    </div>'''
    return save("fig5-2", frame("工作台", "工作台", body))


# ---------------------------------------------------------------- 入驻审核
def ui_application():
    rows = [
        ("AP-20261105-021", "新加坡环宇贸易 Pte. Ltd.", "buyer", "新加坡", "2026-11-05 14:22", "无命中", "green", "待审核"),
        ("AP-20261105-018", "宁波恒动机械有限公司", "supplier", "中国", "2026-11-05 11:08", "无命中", "green", "待审核"),
        ("AP-20261104-015", "Kaya Metals Co., Ltd.", "supplier", "越南", "2026-11-04 17:45", "1 条疑似", "orange", "待尽调"),
        ("AP-20261103-009", "青岛海润包装股份", "buyer,supplier", "中国", "2026-11-03 09:31", "无命中", "green", "审核通过"),
        ("AP-20261102-006", "Greenline GmbH", "supplier", "德国", "2026-11-02 16:12", "无命中", "green", "已开通"),
    ]
    trs = "".join(
        f'<tr><td class="link">{a}</td><td>{b}</td><td><span class="tag {"blue" if r=="buyer" else "purple" if "supplier" in r else "gray"}">{ {"buyer":"采购商","supplier":"供应商","buyer,supplier":"双重身份"}[r] }</span></td>'
        f'<td>{c}</td><td class="muted">{d}</td><td><span class="tag {sc}">{se}</span></td>'
        f'<td><span class="tag {"orange" if st=="待审核" else "blue" if st=="待尽调" else "green"}">{st}</span></td>'
        f'<td><span class="link">审核</span> <span class="muted" style="margin-left:10px">详情</span></td></tr>'
        for a, b, r, c, d, se, sc, st in rows)
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">入驻预审</div><div class="page-sub" style="margin:0">企业注册申请、资质材料与制裁名单筛查结果</div></div>
      <div style="display:flex;gap:10px"><div class="btn plain">批量导出</div><div class="btn primary">刷新列表</div></div>
    </div>
    <div class="card" style="padding:16px 20px;margin-bottom:16px;display:flex;gap:14px;align-items:center">
      <div class="input" style="width:240px">{ICON["search"].replace("<svg","<svg style='width:16px;height:16px;stroke:#94a3b8;fill:none;stroke-width:1.7;margin-right:8px'")}<span style="color:#94a3b8">搜索企业名称 / 申请编号</span></div>
      <div class="input select" style="width:150px;justify-content:space-between">全部身份</div>
      <div class="input select" style="width:150px;justify-content:space-between">全部状态</div>
      <div class="input select" style="width:160px;justify-content:space-between">全部国家/地区</div>
      <div class="btn primary" style="margin-left:auto">查询</div><div class="btn ghost">重置</div>
    </div>
    <div class="row">
      <div class="card" style="flex:1;overflow:hidden">
        <div style="padding:16px 20px;display:flex;justify-content:space-between;align-items:center">
          <div style="font-size:15px;font-weight:600">入驻申请列表 <span class="muted" style="font-weight:400">共 27 条</span></div>
          <div style="display:flex;gap:6px"><span class="tag blue">待审核 4</span><span class="tag orange">待尽调 2</span></div>
        </div>
        <table><thead><tr><th>申请编号</th><th>企业名称</th><th>申请身份</th><th>国家/地区</th><th>提交时间</th><th>制裁筛查</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{trs}</tbody></table>
      </div>
      <div class="card" style="width:430px;padding:20px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
          <div style="font-size:15px;font-weight:600">申请详情</div><span class="tag orange">待审核</span>
        </div>
        <div style="padding:16px;border-radius:10px;background:#f8fafc;margin-bottom:14px">
          <div style="font-size:16px;font-weight:700;color:#0f172a">Kaya Metals Co., Ltd.</div>
          <div class="muted" style="margin-top:5px">AP-20261104-015 · 越南 · 供应商</div>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px 16px;font-size:12.5px">
          <div><div class="muted">统一社会信用代码</div><div style="margin-top:4px">VN-0316-8821</div></div>
          <div><div class="muted">成立年份</div><div style="margin-top:4px">2012</div></div>
          <div><div class="muted">主营品类</div><div style="margin-top:4px">金属结构件 / 铸锻件</div></div>
          <div><div class="muted">年产能</div><div style="margin-top:4px">18,000 吨</div></div>
          <div><div class="muted">出口经验</div><div style="margin-top:4px">4 年 · 12 个国家</div></div>
          <div><div class="muted">联系人</div><div style="margin-top:4px">Nguyen Van An</div></div>
        </div>
        <div style="margin-top:16px;padding:14px;border-radius:10px;background:#fff7ed;border:1px solid #fed7aa">
          <div style="font-size:12.5px;font-weight:600;color:#c2410c;margin-bottom:6px">制裁名单筛查提示</div>
          <div style="font-size:12px;color:#9a3412;line-height:1.75">命中 1 条疑似记录（相似度 82%），建议转尽职调查并核对股权结构。</div>
        </div>
        <div style="margin-top:18px;display:flex;gap:10px">
          <div class="btn primary" style="flex:1;justify-content:center">审核通过</div>
          <div class="btn plain" style="flex:1;justify-content:center">驳回申请</div>
          <div class="btn ghost" style="justify-content:center">发起尽调</div>
        </div>
      </div>
    </div>'''
    return save("fig5-3", frame("入驻预审", "入驻预审", body))


# ---------------------------------------------------------------- 企业画像
def ui_profile():
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">企业画像</div><div class="page-sub" style="margin:0">画像将向量化进入供应商库，用于采购需求智能匹配</div></div>
      <div style="display:flex;gap:10px"><div class="btn plain">预览买家视角</div><div class="btn primary">保存画像</div></div>
    </div>
    <div class="row">
      <div style="flex:1;display:flex;flex-direction:column;gap:16px">
        <div class="card" style="padding:20px 22px">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:18px">
            <div style="font-size:15px;font-weight:600">基本信息</div><span class="tag green">已认证</span>
          </div>
          <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px 20px">
            <div class="field"><label>企业全称</label><div class="input filled">宁波恒动机械有限公司</div></div>
            <div class="field"><label>统一社会信用代码</label><div class="input filled">91330200MA2H8K7X6P</div></div>
            <div class="field"><label>成立年份</label><div class="input filled">2008</div></div>
            <div class="field"><label>所在国家 / 地区</label><div class="input select filled">中国 · 浙江省宁波市</div></div>
            <div class="field"><label>企业类型</label><div class="input select filled">生产制造型企业</div></div>
            <div class="field"><label>员工规模</label><div class="input select filled">500 - 1000 人</div></div>
          </div>
        </div>
        <div class="card" style="padding:20px 22px">
          <div style="font-size:15px;font-weight:600;margin-bottom:18px">供应能力</div>
          <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px 20px">
            <div class="field"><label>供应品类</label><div class="input filled">精密轴承 / 传动件</div></div>
            <div class="field"><label>年产能</label><div class="input filled">120 万套</div></div>
            <div class="field"><label>主要产品</label><div class="input filled">深沟球轴承、圆锥滚子轴承</div></div>
            <div class="field"><label>出口经验</label><div class="input select filled">5 年 · 欧美 / 东南亚</div></div>
            <div class="field"><label>交付周期</label><div class="input filled">25 - 35 天</div></div>
            <div class="field"><label>主要客户</label><div class="input filled">博世、舍弗勒、三一重工</div></div>
          </div>
          <div style="margin-top:18px;display:flex;gap:8px"><span class="tag blue">ISO 9001</span><span class="tag blue">IATF 16949</span><span class="tag blue">ISO 14001</span><span class="tag gray">+ 上传资质</span></div>
        </div>
        <div class="card" style="padding:20px 22px">
          <div style="font-size:15px;font-weight:600;margin-bottom:16px">履约表现</div>
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:18px">
            <div><div class="muted">按时交付率</div><div style="font-size:24px;font-weight:700;color:#15803d;margin:6px 0">97.8%</div><div class="pbar"><i style="width:97.8%"></i></div></div>
            <div><div class="muted">验收合格率</div><div style="font-size:24px;font-weight:700;color:#0066ff;margin:6px 0">99.2%</div><div class="pbar"><i style="width:99.2%"></i></div></div>
            <div><div class="muted">平均响应时长</div><div style="font-size:24px;font-weight:700;color:#1a1a2e;margin:6px 0">2.4 h</div><div class="pbar"><i style="width:74%"></i></div></div>
            <div><div class="muted">历史成交额</div><div style="font-size:24px;font-weight:700;color:#1a1a2e;margin:6px 0">¥ 3,860 万</div><div class="pbar"><i style="width:88%"></i></div></div>
          </div>
        </div>
      </div>
      <div style="width:330px;display:flex;flex-direction:column;gap:16px">
        <div class="card" style="padding:24px;display:flex;flex-direction:column;align-items:center">
          <div style="font-size:15px;font-weight:600;align-self:flex-start">画像完整度</div>
          <div class="ring" style="margin:22px 0 14px"><span>86<small>%</small></span></div>
          <div class="muted" style="text-align:center;line-height:1.8">补充「财务信息」与「近三年出口记录」<br>可提升至 95%，获得更多匹配曝光</div>
          <div class="btn primary" style="width:100%;justify-content:center;margin-top:18px">继续完善</div>
        </div>
        <div class="card" style="padding:20px 22px">
          <div style="font-size:15px;font-weight:600;margin-bottom:14px">AI 画像摘要</div>
          <div style="font-size:12.5px;line-height:1.9;color:#475569">华东地区精密轴承制造商，具备 IATF 16949 汽车行业认证，近三年对欧美出口稳定增长；产能与交期表现优于同品类 82% 的供应商，适合中高精度、批量稳定的采购需求。</div>
          <div style="margin-top:14px;display:flex;flex-wrap:wrap;gap:7px"><span class="tag blue">高匹配品类</span><span class="tag green">交付稳定</span><span class="tag purple">出口经验丰富</span></div>
        </div>
        <div class="card" style="padding:20px 22px">
          <div style="font-size:15px;font-weight:600;margin-bottom:12px">认证材料</div>
          <div style="display:flex;flex-direction:column;gap:10px">
            <div style="display:flex;justify-content:space-between;font-size:12.5px"><span>营业执照.pdf</span><span class="tag green">已核验</span></div>
            <div style="display:flex;justify-content:space-between;font-size:12.5px"><span>IATF16949证书.pdf</span><span class="tag green">已核验</span></div>
            <div style="display:flex;justify-content:space-between;font-size:12.5px"><span>近三年审计报告.pdf</span><span class="tag orange">待补充</span></div>
          </div>
        </div>
      </div>
    </div>'''
    return save("fig5-4", frame("企业画像", "企业画像", body))


# ---------------------------------------------------------------- 智能匹配
def ui_match():
    suppliers = [
        ("宁波恒动机械有限公司", 92, "中国 · 宁波", ["精密轴承", "IATF 16949", "交期 28 天"], "品类完全匹配，具备汽车级质量认证；历史按时交付率 97.8%，产能可覆盖本次 12 万套需求。", "#15803d"),
        ("苏州精工传动科技", 87, "中国 · 苏州", ["深沟球轴承", "ISO 9001", "交期 32 天"], "品类与规格匹配，价格低于品类均值 6.4%；出口经验略少，建议关注物流节点。", "#0066ff"),
        ("Kaya Metals Co., Ltd.", 81, "越南 · 海防", ["金属结构件", "出口欧美", "交期 40 天"], "具备成本优势与东盟原产地优势；制裁筛查存在 1 条疑似记录，建议先完成尽调。", "#c2410c"),
    ]
    cards = "".join(
        f'''<div class="card" style="padding:20px 22px;margin-bottom:14px">
          <div style="display:flex;gap:18px;align-items:flex-start">
            <div style="width:74px;height:74px;border-radius:16px;background:linear-gradient(135deg,#eef6ff,#dbeafe);display:flex;flex-direction:column;align-items:center;justify-content:center;flex:0 0 74px">
              <div style="font-size:26px;font-weight:800;color:{col}">{score}</div><div style="font-size:10px;color:#64748b">匹配分</div></div>
            <div style="flex:1">
              <div style="display:flex;align-items:center;gap:10px"><span style="font-size:16px;font-weight:700;color:#0f172a">{name}</span><span class="tag green">推荐</span></div>
              <div class="muted" style="margin-top:4px">{loc}</div>
              <div style="display:flex;gap:7px;margin-top:10px">{''.join(f'<span class="tag blue">{t}</span>' for t in tags)}</div>
              <div style="margin-top:12px;padding:12px 14px;border-radius:9px;background:#f8fafc;font-size:12.5px;line-height:1.8;color:#475569"><b style="color:#0066ff">匹配理由：</b>{reason}</div>
            </div>
            <div style="display:flex;flex-direction:column;gap:8px;flex:0 0 108px">
              <div class="btn primary" style="justify-content:center">发起对接</div>
              <div class="btn plain" style="justify-content:center">查看画像</div>
            </div>
          </div>
          <div style="display:flex;gap:22px;margin-top:14px;padding-top:14px;border-top:1px solid #f1f5f9">
            <div style="flex:1"><div class="muted">品类匹配</div><div class="pbar" style="margin-top:6px"><i style="width:{score}%"></i></div></div>
            <div style="flex:1"><div class="muted">资质</div><div class="pbar" style="margin-top:6px"><i style="width:{min(100,score+4)}%"></i></div></div>
            <div style="flex:1"><div class="muted">产能</div><div class="pbar" style="margin-top:6px"><i style="width:{score-6}%"></i></div></div>
            <div style="flex:1"><div class="muted">交付</div><div class="pbar" style="margin-top:6px"><i style="width:{score-3}%"></i></div></div>
          </div>
        </div>'''
        for name, score, loc, tags, reason, col in suppliers)
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">智能匹配结果</div><div class="page-sub" style="margin:0">采购需求 REQ-20261105-008 · 精密轴承 6205-2RS 12 万套</div></div>
      <div style="display:flex;gap:10px"><div class="btn plain">调整匹配条件</div><div class="btn primary">批量邀请报价</div></div>
    </div>
    <div class="card" style="padding:16px 20px;margin-bottom:16px;display:flex;align-items:center;gap:26px;background:linear-gradient(100deg,#f0f7ff,#fbfdff)">
      <div><div class="muted">采购品类</div><div style="font-weight:600;margin-top:4px">精密轴承 / 6205-2RS</div></div>
      <div><div class="muted">数量</div><div style="font-weight:600;margin-top:4px">120,000 套</div></div>
      <div><div class="muted">目标交期</div><div style="font-weight:600;margin-top:4px">2026-12-20</div></div>
      <div><div class="muted">目标地区</div><div style="font-weight:600;margin-top:4px">中国 / 东南亚</div></div>
      <div><div class="muted">资质要求</div><div style="font-weight:600;margin-top:4px">ISO 9001 及以上</div></div>
      <div style="margin-left:auto;text-align:right"><div class="muted">AI 召回 36 家 · 精排 Top 10</div><div style="font-weight:600;color:#0066ff;margin-top:4px">当前展示 3 家</div></div>
    </div>
    <div class="row">
      <div style="width:250px;flex:0 0 250px;display:flex;flex-direction:column;gap:14px">
        <div class="card" style="padding:18px">
          <div style="font-size:14px;font-weight:600;margin-bottom:14px">筛选条件</div>
          <div style="font-size:12.5px;color:#64748b;margin-bottom:8px">地域范围</div>
          <div style="display:flex;flex-wrap:wrap;gap:7px"><span class="tag blue">中国</span><span class="tag blue">东南亚</span><span class="tag gray">欧洲</span></div>
          <div style="font-size:12.5px;color:#64748b;margin:16px 0 8px">最低匹配分</div>
          <div class="input" style="height:34px">80 分</div>
          <div style="font-size:12.5px;color:#64748b;margin:16px 0 8px">风险筛查</div>
          <div style="display:flex;flex-direction:column;gap:8px;font-size:12.5px"><span>☑ 排除制裁名单命中</span><span>☑ 排除高风险企业</span><span>☑ 仅展示已认证企业</span></div>
        </div>
        <div class="card" style="padding:18px;background:#f1f7ff">
          <div style="font-size:13px;font-weight:600;color:#1d4ed8">匹配策略</div>
          <div style="font-size:12px;color:#4770a8;line-height:1.8;margin-top:8px">向量召回 + 品类/资质/产能/地域/出口经验/画像完整度六维加权，权重可在系统管理中调整。</div>
        </div>
      </div>
      <div style="flex:1">{cards}</div>
    </div>'''
    return save("fig5-5", frame("采购需求", "采购需求 / 智能匹配", body))


# ---------------------------------------------------------------- 比价授标
def ui_compare():
    rows = [
        ("宁波恒动机械", "¥ 862,400", "7.19", "28 天", "30% 预付", "3,860", "92.6", "green", "推荐授标"),
        ("苏州精工传动", "¥ 898,000", "7.48", "32 天", "30% 预付", "4,120", "87.1", "blue", "备选"),
        ("Kaya Metals", "¥ 806,400", "6.72", "40 天", "20% 预付", "5,980", "81.4", "orange", "风险关注"),
        ("温州法兰管件", "¥ 935,200", "7.79", "26 天", "40% 预付", "5,200", "76.8", "gray", "价格偏高"),
    ]
    def score_color(v):
        return "#15803d" if float(v) >= 90 else "#0066ff" if float(v) >= 85 else "#c2410c"
    trs = "".join(
        f'<tr><td><b>{n}</b></td><td>{p}</td><td>¥ {u}</td><td>{d}</td><td>{pay}</td><td>¥ {tco}</td>'
        f'<td><b style="color:{score_color(s)}">{s}</b></td>'
        f'<td><span class="tag {c}">{r}</span></td></tr>' for n,p,u,d,pay,tco,s,c,r in rows)
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">比价与授标</div><div class="page-sub" style="margin:0">询盘 INQ-20261103-008 · 精密轴承 6205-2RS · 报价截止 2026-11-08 18:00</div></div>
      <div style="display:flex;gap:10px"><div class="btn plain">导出比价单</div><div class="btn primary">确认授标</div></div>
    </div>
    <div class="row" style="margin-bottom:16px">
      <div class="card" style="flex:1;padding:0">
        <div style="padding:16px 20px;display:flex;justify-content:space-between;align-items:center">
          <div><div style="font-size:15px;font-weight:600">报价汇总</div><div class="muted" style="margin-top:3px">已收到 4 份报价 · 币种 CNY</div></div>
          <div style="display:flex;gap:6px"><span class="tag blue">单价</span><span class="tag orange">TCO 总成本</span><span class="tag purple">AI 评分</span></div>
        </div>
        <table><thead><tr><th>供应商</th><th>单价</th><th>总价</th><th>交期</th><th>付款条件</th><th>TCO 成本</th><th>AI 评分</th><th>建议</th></tr></thead>
        <tbody>{trs}</tbody></table>
        <div style="padding:16px 20px;display:flex;gap:30px;border-top:1px solid #f1f5f9">
          <div><div class="muted">最低报价</div><div style="font-weight:700;margin-top:3px">¥ 806,400</div></div>
          <div><div class="muted">最低 TCO</div><div style="font-weight:700;margin-top:3px">¥ 3,860</div></div>
          <div><div class="muted">平均单价</div><div style="font-weight:700;margin-top:3px">¥ 7.30</div></div>
          <div><div class="muted">异常报价</div><div style="font-weight:700;color:#c2410c;margin-top:3px">1 份</div></div>
        </div>
      </div>
      <div class="card" style="width:430px;padding:20px">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px"><span style="font-size:15px;font-weight:600">AI 综合比价分析</span><span class="tag purple">DeepSeek</span></div>
        <div class="muted" style="margin-bottom:16px">综合价格、交期、付款条件、履约风险</div>
        <div style="padding:14px;border-radius:10px;background:#f0fdf4;border:1px solid #bbf7d0;margin-bottom:14px">
          <div style="font-size:13px;font-weight:700;color:#15803d;margin-bottom:6px">推荐：宁波恒动机械有限公司</div>
          <div style="font-size:12.5px;line-height:1.85;color:#166534">TCO 成本最低且交期可控；汽车级质量体系与 97.8% 按时交付率可覆盖本次批量需求。综合评分 92.6，显著高于其他报价。</div>
        </div>
        <div style="font-size:12.5px;color:#64748b;margin-bottom:8px">风险提示</div>
        <div style="display:flex;flex-direction:column;gap:8px;font-size:12.5px;color:#475569">
          <div>• Kaya Metals 报价低于品类均值 12%，需核实原材料来源与产能真实性。</div>
          <div>• 温州法兰交期最短但付款条件最严，资金占用成本增加约 ¥ 2.1 万。</div>
          <div>• 建议合同约定分批交付，首批 4 万套于 12 月 5 日前到货。</div>
        </div>
        <div style="margin-top:16px;padding:12px 14px;border-radius:9px;background:#f8fafc;font-size:12px;color:#64748b;line-height:1.75">授标后将自动生成授标理由并通知中标 / 未中标供应商，同时进入合同签署流程。</div>
        <div style="display:flex;gap:10px;margin-top:16px"><div class="btn primary" style="flex:1;justify-content:center">采纳建议并授标</div><div class="btn plain" style="justify-content:center">查看分析详情</div></div>
      </div>
    </div>
    <div class="card" style="padding:18px 22px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px"><div style="font-size:15px;font-weight:600">TCO 成本拆解对比</div><div class="muted">单位：万元</div></div>
      <div style="display:flex;gap:24px">
        <div style="flex:1"><div style="font-size:12.5px;color:#64748b;margin-bottom:8px">宁波恒动机械</div><div style="display:flex;height:26px;border-radius:6px;overflow:hidden"><i style="width:72%;background:#0066ff"></i><i style="width:12%;background:#4da3ff"></i><i style="width:8%;background:#93c5fd"></i><i style="width:8%;background:#bfdbfe"></i></div><div style="display:flex;gap:14px;margin-top:8px;font-size:11px;color:#94a3b8"><span>货值 72%</span><span>物流 12%</span><span>关税 8%</span><span>资金成本 8%</span></div></div>
        <div style="flex:1"><div style="font-size:12.5px;color:#64748b;margin-bottom:8px">苏州精工传动</div><div style="display:flex;height:26px;border-radius:6px;overflow:hidden"><i style="width:74%;background:#15803d"></i><i style="width:11%;background:#4ade80"></i><i style="width:8%;background:#86efac"></i><i style="width:7%;background:#bbf7d0"></i></div><div style="display:flex;gap:14px;margin-top:8px;font-size:11px;color:#94a3b8"><span>货值 74%</span><span>物流 11%</span><span>关税 8%</span><span>资金成本 7%</span></div></div>
        <div style="flex:1"><div style="font-size:12.5px;color:#64748b;margin-bottom:8px">Kaya Metals</div><div style="display:flex;height:26px;border-radius:6px;overflow:hidden"><i style="width:66%;background:#c2410c"></i><i style="width:18%;background:#fb923c"></i><i style="width:9%;background:#fdba74"></i><i style="width:7%;background:#fed7aa"></i></div><div style="display:flex;gap:14px;margin-top:8px;font-size:11px;color:#94a3b8"><span>货值 66%</span><span>物流 18%</span><span>关税 9%</span><span>资金成本 7%</span></div></div>
      </div>
    </div>'''
    return save("fig5-6", frame("比价管理", "采购管理 / 比价管理", body))


# ---------------------------------------------------------------- 交付风险
def ui_delivery():
    rows = [
        ("DLV-20261102-016", "青岛海润包装", "食品级包装膜", "2026-11-22", "宁波港 → 汉堡港", "在途", "低", "green", "台风路径偏北，预计不影响靠港"),
        ("DLV-20261030-012", "温州法兰管件", "不锈钢法兰", "2026-11-18", "上海港 → 洛杉矶", "在途", "高", "red", "船期延误 4 天，已触发延期预警"),
        ("DLV-20261028-009", "宁波恒动机械", "精密轴承", "2026-11-15", "宁波港 → 鹿特丹", "已到港", "中", "orange", "目的港拥堵，提柜等待约 3 天"),
        ("DLV-20261026-004", "苏州智控电子", "PLC 模块", "2026-11-10", "空运 PVG → FRA", "待验收", "低", "green", "已签收，等待采购方验收"),
    ]
    trs = "".join(
        f'<tr><td class="link">{no}</td><td>{sup}</td><td>{item}</td><td>{eta}</td><td>{route}</td><td><span class="tag {"blue" if st=="在途" else "green" if st=="待验收" else "gray"}">{st}</span></td>'
        f'<td><span class="tag {color}">{lvl}</span></td><td>{tip}</td></tr>'
        for no,sup,item,eta,route,st,lvl,color,tip in rows)
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">交付风险监控</div><div class="page-sub" style="margin:0">在途交付、物流轨迹与外部风险信号的实时聚合</div></div>
      <div style="display:flex;gap:10px"><div class="btn plain">风险规则配置</div><div class="btn primary">生成处置建议</div></div>
    </div>
    <div class="stats">
      <div class="card stat"><div class="lab">在途交付</div><div class="num">24</div><div class="trend">覆盖 9 个国家和地区</div></div>
      <div class="card stat"><div class="lab">延期风险</div><div class="num" style="color:#c2410c">3</div><div class="trend down">1 笔已超承诺交期</div></div>
      <div class="card stat"><div class="lab">高风险订单</div><div class="num" style="color:#b91c1c">1</div><div class="trend down">需 24 小时内处置</div></div>
      <div class="card stat"><div class="lab">待验收</div><div class="num" style="color:#15803d">6</div><div class="trend">平均等待 1.8 天</div></div>
    </div>
    <div class="row" style="margin-bottom:16px">
      <div class="card" style="flex:1;padding:18px 20px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px"><div style="font-size:15px;font-weight:600">风险事件时间线</div><span class="tag red">1 条高风险</span></div>
        <div style="display:flex;flex-direction:column;gap:14px">
          <div style="display:flex;gap:12px"><div style="width:10px;height:10px;border-radius:50%;background:#b91c1c;margin-top:5px;flex:0 0 10px"></div><div><div style="font-size:13px;font-weight:600">DLV-20261030-012 · 船期延误 4 天</div><div class="muted" style="margin-top:3px">船公司通知：受太平洋航线运力调整影响，预计到港时间延至 11 月 24 日。</div><div style="font-size:11.5px;color:#b91c1c;margin-top:5px">建议：启用备选供应商 / 与客户协商分批交付</div></div></div>
          <div style="display:flex;gap:12px"><div style="width:10px;height:10px;border-radius:50%;background:#c2410c;margin-top:5px;flex:0 0 10px"></div><div><div style="font-size:13px;font-weight:600">DLV-20261028-009 · 目的港拥堵</div><div class="muted" style="margin-top:3px">鹿特丹港平均提柜等待 3 天，暂未超出合同允许延期天数。</div></div></div>
          <div style="display:flex;gap:12px"><div style="width:10px;height:10px;border-radius:50%;background:#15803d;margin-top:5px;flex:0 0 10px"></div><div><div style="font-size:13px;font-weight:600">DLV-20261026-004 · 已完成签收</div><div class="muted" style="margin-top:3px">货物于 11 月 5 日 14:20 签收，等待验收。</div></div></div>
        </div>
      </div>
      <div class="card" style="width:430px;padding:18px 20px;background:linear-gradient(140deg,#fff7ed,#fffdf8)">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px"><span style="font-size:15px;font-weight:600">AI 应急处置建议</span><span class="tag purple">实时生成</span></div>
        <div style="font-size:12.5px;line-height:1.9;color:#7c2d12">针对 DLV-20261030-012：</div>
        <div style="font-size:12.5px;line-height:1.9;color:#475569;margin-top:6px">1. 联系备选供应商苏州精工传动，确认 2 万套替代产能；<br>2. 评估改走宁波 → 长滩航线，预计可缩短 2 天；<br>3. 与采购方协商将合同允许延期天数由 5 天调整为 8 天。</div>
        <div style="display:flex;gap:10px;margin-top:16px"><div class="btn primary" style="flex:1;justify-content:center">生成处置工单</div><div class="btn plain" style="justify-content:center">查看方案</div></div>
      </div>
    </div>
    <div class="card" style="padding:0">
      <div style="padding:16px 20px;display:flex;justify-content:space-between;align-items:center"><div style="font-size:15px;font-weight:600">交付单列表</div><div class="muted">共 24 条在途 / 待验收记录</div></div>
      <table><thead><tr><th>交付单号</th><th>供应商</th><th>物料</th><th>承诺交期</th><th>运输路线</th><th>状态</th><th>风险等级</th><th>风险说明</th></tr></thead>
      <tbody>{trs}</tbody></table>
    </div>'''
    return save("fig5-7", frame("风险监控", "可信交付 / 风险监控", body))


# ---------------------------------------------------------------- AI 助手
def ui_ai():
    body = f'''
    <div style="display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:16px">
      <div><div class="page-title">AI 采购助理</div><div class="page-sub" style="margin:0">基于企业知识库与业务数据的多轮对话，写操作需人工确认</div></div>
      <div style="display:flex;gap:10px"><span class="tag green">知识库已连接</span><span class="tag blue">RAG 模式</span></div>
    </div>
    <div class="row" style="height:770px">
      <div class="card" style="flex:1;display:flex;flex-direction:column;overflow:hidden">
        <div style="padding:16px 20px;border-bottom:1px solid #f1f5f9;display:flex;justify-content:space-between;align-items:center">
          <div style="display:flex;align-items:center;gap:10px"><div style="width:34px;height:34px;border-radius:10px;background:linear-gradient(135deg,#0066ff,#00b4ff);display:grid;place-items:center">{ICON["bot"].replace("<svg","<svg style='width:18px;height:18px;stroke:#fff;fill:none;stroke-width:1.7'")}</div><div><div style="font-weight:600">智能采购助手</div><div class="muted" style="font-size:11px">已引用 6 篇制度/合同文档</div></div></div>
          <div style="display:flex;gap:8px"><span class="tag gray">新会话</span><span class="tag gray">历史记录</span></div>
        </div>
        <div style="flex:1;padding:24px 28px;display:flex;flex-direction:column;gap:20px;background:#fbfcfe">
          <div style="align-self:flex-end;max-width:70%;background:#0066ff;color:#fff;padding:13px 16px;border-radius:12px 12px 3px 12px;font-size:13px;line-height:1.75">帮我对比一下询盘 INQ-20261103-008 的四家报价，并给出授标建议。</div>
          <div style="max-width:82%;align-self:flex-start">
            <div style="display:flex;gap:9px;align-items:center;margin-bottom:8px"><div style="width:26px;height:26px;border-radius:8px;background:#e8f1ff;display:grid;place-items:center;color:#0066ff;font-size:12px;font-weight:700">AI</div><span class="muted" style="font-size:11.5px">正在分析报价数据 · 已调用 3 个业务工具</span></div>
            <div style="background:#fff;border:1px solid #eef2f7;border-radius:12px;padding:16px 18px;box-shadow:0 2px 8px rgba(15,23,42,.04)">
              <div style="font-size:13px;line-height:1.9;color:#334155">已完成对 <b>INQ-20261103-008</b> 四家报价的 TCO 拆解与风险校验，结论如下：</div>
              <table style="margin-top:12px;border:1px solid #eef2f7;border-radius:8px;overflow:hidden"><thead><tr><th>供应商</th><th>总价</th><th>交期</th><th>TCO</th><th>评分</th></tr></thead><tbody>
                <tr><td><b>宁波恒动机械</b></td><td>¥ 862,400</td><td>28 天</td><td>¥ 3,860</td><td><b style="color:#15803d">92.6</b></td></tr>
                <tr><td>苏州精工传动</td><td>¥ 898,000</td><td>32 天</td><td>¥ 4,120</td><td>87.1</td></tr>
                <tr><td>Kaya Metals</td><td>¥ 806,400</td><td>40 天</td><td>¥ 5,980</td><td>81.4</td></tr>
                <tr><td>温州法兰管件</td><td>¥ 935,200</td><td>26 天</td><td>¥ 5,200</td><td>76.8</td></tr>
              </tbody></table>
              <div style="font-size:12.5px;line-height:1.9;color:#475569;margin-top:12px"><b>建议：</b>选择宁波恒动机械。其 TCO 最低、准时交付率 97.8%，且具备 IATF 16949 认证；Kaya Metals 虽单价最低，但交期 40 天且存在制裁筛查疑似记录，不建议本次授标。</div>
              <div style="margin-top:14px;padding:12px 14px;border-radius:9px;background:#fff7ed;border:1px solid #fed7aa">
                <div style="font-size:12.5px;font-weight:600;color:#c2410c;margin-bottom:5px">需要您确认的写操作</div>
                <div style="font-size:12px;color:#9a3412;line-height:1.7">为询盘 INQ-20261103-008《精密轴承 6205-2RS 采购》创建授标记录，授标对象：宁波恒动机械有限公司，数量 120,000 套。</div>
                <div style="display:flex;gap:8px;margin-top:10px"><div class="btn primary" style="height:30px;font-size:12px">确认执行</div><div class="btn plain" style="height:30px;font-size:12px">取消</div></div>
              </div>
            </div>
          </div>
        </div>
        <div style="padding:16px 20px;border-top:1px solid #f1f5f9;background:#fff">
          <div style="height:46px;border:1px solid #e2e8f0;border-radius:10px;display:flex;align-items:center;padding:0 14px;color:#94a3b8;font-size:13px">继续提问，例如「把这单的交期延后一周」…<span style="margin-left:auto;background:#0066ff;color:#fff;border-radius:7px;padding:6px 14px;font-size:12.5px">发送</span></div>
        </div>
      </div>
      <div style="width:340px;display:flex;flex-direction:column;gap:16px">
        <div class="card" style="padding:20px">
          <div style="font-size:15px;font-weight:600;margin-bottom:14px">本次会话引用</div>
          <div style="display:flex;flex-direction:column;gap:12px">
            <div style="padding:12px;border-radius:9px;background:#f8fafc"><div style="font-size:12.5px;font-weight:600">询盘 INQ-20261103-008</div><div class="muted" style="margin-top:4px">精密轴承 6205-2RS · 12 万套</div></div>
            <div style="padding:12px;border-radius:9px;background:#f8fafc"><div style="font-size:12.5px;font-weight:600">报价数据 × 4</div><div class="muted" style="margin-top:4px">含 TCO 与历史履约记录</div></div>
            <div style="padding:12px;border-radius:9px;background:#f8fafc"><div style="font-size:12.5px;font-weight:600">《采购授标管理办法》</div><div class="muted" style="margin-top:4px">知识库文档 · 第 3.2 节</div></div>
          </div>
        </div>
        <div class="card" style="padding:20px">
          <div style="font-size:15px;font-weight:600;margin-bottom:12px">可以这样问我</div>
          <div style="display:flex;flex-direction:column;gap:9px;font-size:12.5px;color:#475569">
            <div style="padding:10px 12px;border-radius:8px;border:1px solid #eef2f7">总结本周所有延期交付订单</div>
            <div style="padding:10px 12px;border-radius:8px;border:1px solid #eef2f7">起草一份采购合同条款</div>
            <div style="padding:10px 12px;border-radius:8px;border:1px solid #eef2f7">这家供应商的尽调风险有哪些？</div>
          </div>
        </div>
        <div class="card" style="padding:20px;background:#f1f7ff">
          <div style="font-size:13px;font-weight:600;color:#1d4ed8">安全提示</div>
          <div style="font-size:12px;color:#4770a8;line-height:1.8;margin-top:8px">AI 只能生成待确认提案，授标、改价、关闭询盘等写操作必须由您点击确认后才会落库，并记录审计日志。</div>
        </div>
      </div>
    </div>'''
    return save("fig5-8", frame("AI 知识库", "AI 助手", body))


if __name__ == "__main__":
    for fn in [ui_login, ui_dashboard, ui_application, ui_profile, ui_match, ui_compare, ui_delivery, ui_ai]:
        print(fn())
