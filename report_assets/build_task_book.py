#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《毕业设计（论文）任务书》Word 文档。

对应课题：VANPINE SRM 全球供应商管理系统的设计与实现
学生：王兵（2320118011，23 计科，第 5 组）

用法：
    python3 report_assets/build_task_book.py
输出：
    仓库根目录下的 毕业设计任务书-王兵-VANPINE SRM全球供应商管理系统.docx
"""

import os

from docx import Document
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

OUTPUT = "毕业设计任务书-王兵-VANPINE SRM全球供应商管理系统.docx"

# ---------------------------------------------------------------- 工具函数


def set_run_font(run, cn_font="宋体", en_font="Times New Roman", size=12, bold=False):
    """同时设置西文与中文字体（python-docx 默认不写 eastAsia）。"""
    run.font.name = en_font
    run.font.size = Pt(size)
    run.font.bold = bold
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), en_font)
    rfonts.set(qn("w:hAnsi"), en_font)
    rfonts.set(qn("w:eastAsia"), cn_font)


def add_para(doc, text, cn_font="宋体", size=12, bold=False,
             indent=True, align=WD_ALIGN_PARAGRAPH.JUSTIFY,
             line_spacing=1.5, space_before=0, space_after=0):
    p = doc.add_paragraph()
    p.alignment = align
    pf = p.paragraph_format
    pf.line_spacing = line_spacing
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    if indent:
        pf.first_line_indent = Pt(size * 2)  # 首行缩进两字符
    run = p.add_run(text)
    set_run_font(run, cn_font, "Times New Roman", size, bold)
    return p


def add_h1(doc, text, page_break=False):
    p = add_para(doc, text, cn_font="黑体", size=14, bold=True,
                 indent=False, align=WD_ALIGN_PARAGRAPH.LEFT,
                 line_spacing=1.5, space_before=12, space_after=6)
    p.paragraph_format.keep_with_next = True
    if page_break:
        p.paragraph_format.page_break_before = True
    return p


def add_h2(doc, text):
    p = add_para(doc, text, cn_font="楷体", size=12, bold=True,
                 indent=False, align=WD_ALIGN_PARAGRAPH.LEFT,
                 line_spacing=1.5, space_before=6, space_after=3)
    p.paragraph_format.keep_with_next = True
    return p


def prevent_row_split(row):
    """禁止表格行跨页拆分。"""
    tr_pr = row._tr.get_or_add_trPr()
    tr_pr.append(OxmlElement("w:cantSplit"))


def set_repeat_header(row):
    """表格跨页时重复表头。"""
    tr_pr = row._tr.get_or_add_trPr()
    tr_pr.append(OxmlElement("w:tblHeader"))


def set_cell_text(cell, text, bold=False, size=10.5,
                  align=WD_ALIGN_PARAGRAPH.CENTER):
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    p = cell.paragraphs[0]
    p.alignment = align
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(4)
    if text:
        run = p.add_run(text)
        set_run_font(run, "宋体", "Times New Roman", size, bold)


def add_page_number_footer(section):
    """页脚居中页码。"""
    p = section.footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    set_run_font(run, "宋体", "Times New Roman", 9)
    for instr, kind in (("begin", None), (None, "PAGE"), ("end", None)):
        if kind:
            el = OxmlElement("w:instrText")
            el.set(qn("xml:space"), "preserve")
            el.text = kind
        else:
            el = OxmlElement("w:fldChar")
            el.set(qn("w:fldCharType"), instr)
        run._r.append(el)


# ---------------------------------------------------------------- 文档内容

INFO_ROWS = [
    ("学    院", "", "专    业", "计算机科学与技术"),
    ("学生姓名", "王兵", "学    号", "2320118011"),
    ("班    级", "23 计科（第 5 组）", "课题性质", "工程设计"),
    ("课题名称", "VANPINE SRM 全球供应商管理系统的设计与实现", None, None),
    ("指导教师", "", "职    称", ""),
    ("下达任务日期", "    年    月    日", "完成期限", "    年    月    日"),
]

BACKGROUND = [
    "近年来，全球经济一体化与跨境电商的快速发展使企业跨国采购规模持续扩大，供应链管理正由企业内部的协同转向全球范围内的协同。"
    "与此不相适应的是，采购企业在供应商寻源、资质核验、询报价、跨境合规与交付履约等环节仍大量依赖人脉推荐、邮件沟通和 Excel 台账，"
    "存在供应商资源分散、信息真伪难辨、报价难以横向对比、合规风险只能事后补救、交付过程不可控以及供应商资产难以沉淀等问题，"
    "传统管理方式已难以支撑日益复杂的跨境采购业务。",

    "与此同时，以 Spring Cloud 为代表的微服务架构、以 Vue 3 为代表的前端工程化技术，以及检索增强生成（RAG）、向量检索与大语言模型等人工智能技术的成熟，"
    "为构建集智能匹配、交易协同与合规风控于一体的供应商关系管理（SRM）平台提供了技术基础。",

    "本课题拟设计并实现一套面向跨境采购的 VANPINE SRM 全球供应商管理系统，覆盖供应商入驻审核、全域画像、智能匹配、询盘报价、"
    "比价授标、电子合同、交付履约与风险监控的完整业务闭环。课题的理论意义在于探索微服务架构、多租户数据隔离、RBAC 权限模型与向量检索技术"
    "相结合的企业级 B2B 系统设计方法；实践意义在于形成一套可运行、可演示的供应链协同平台，为中小采购企业的数字化采购转型提供参考。",
]

RESEARCH_ITEMS = [
    ("（1）平台治理与多租户权限体系研究。", "研究企业入驻申请与资质材料审核流程、租户开通与用户/角色/菜单管理，设计基于 JWT 的认证与网关统一鉴权机制、"
     "服务层租户拦截与数据隔离方案，并实现操作审计日志。"),
    ("（2）供应商全域画像与智能匹配研究。", "研究供应商画像的结构化建模（品类、产能、资质、出口经验、财务与履约记录），"
     "将画像文本向量化并写入向量库；研究向量语义召回与品类、资质、产能、地域、出口经验、画像完整度等多维规则的加权评分方法，并生成可读的匹配理由。"),
    ("（3）尽职调查与合规筛查研究。", "研究制裁名单实时比对、多层股权穿透追溯实际控制人与关联方、诉讼与行政处罚记录汇总等方法；"
     "设计第三方尽调机构的派单流程与独立工作台，实现尽调报告的快速生成与归档。"),
    ("（4）采购交易全链路研究。", "研究采购需求发布、供应商邀请、结构化 RFP/RFQ 询盘生成、在线报价归集、"
     "TCO 全维度成本拆解与异常报价识别、多目标比价与授标理由自动生成，以及电子合同签署的业务流程与状态机设计。"),
    ("（5）交付与物流履约研究。", "研究发货、物流轨迹、收货与验收的节点化管理与关键节点存证，"
     "接入天气与航线数据，实现运输时效影响分析与航线调整、提前发货等运输建议。"),
    ("（6）风险监控与供应保障驾驶舱研究。", "研究将制裁、股权、诉讼、天气与航线等外部风险信号关联到具体供应商与订单，"
     "设计风险分级预警机制与备选供应商、替代航线、调拨等应急处置推演方案，并以可视化驾驶舱聚合展示。"),
    ("（7）AI 助手研究。", "研究基于 RAG 的知识库问答（支持 PDF/Word/Excel 文档解析入库）、多轮对话与上下文压缩、"
     "业务工具调用及写操作人工确认机制，保证 AI 生成结果可控、可审计。"),
    ("（8）系统集成、测试与部署研究。", "研究各微服务之间的接口联调、自动化测试、性能与安全测试方法，"
     "完成系统的容器化部署与运行环境配置。"),
]

REQUIREMENTS = [
    ("（一）技术要求。",
     "后端采用 Java 17、Spring Boot 3、Spring Cloud Alibaba（Nacos、Gateway、OpenFeign），MyBatis-Plus 持久层框架与 MySQL 8 数据库，"
     "并使用 Redis、RabbitMQ 支撑缓存与异步消息；前端采用 Vue 3、Vite、Element Plus、Pinia、Vue Router 与 Tailwind CSS；"
     "AI 服务采用 Python、FastAPI、LangChain、Milvus 向量数据库及大语言模型 API。后端按业务域拆分为平台、交易、供应商、交付、AI 五个微服务，"
     "统一置于同一父 POM 下构建，通过网关 18080 统一对外提供服务。"),
    ("（二）功能与质量要求。",
     "完整实现本任务书所列研究内容的业务流程闭环；多租户数据严格隔离，不得出现跨企业数据串号；AI 服务只负责计算与生成，不持有业务事务，"
     "服务不可用时不阻塞主流程；AI 的写操作工具调用必须经人工确认后执行；接口统一使用 R 统一响应体与全局异常处理；"
     "前端按角色动态生成菜单并配置路由守卫。"),
    ("（三）交付成果要求。",
     "可运行的系统及部署说明；后端、前端与 AI 服务完整源码；数据库建表脚本与初始化数据；需求规格说明书、概要设计说明书等设计文档；"
     "测试用例与测试记录；毕业设计论文。"),
    ("（四）论文撰写要求。",
     "论文结构完整、逻辑清晰，图表规范，参考文献著录格式符合国家标准；正文篇幅与查重率符合学校规定；"
     "系统截图、测试数据等佐证材料须真实反映系统实际运行情况。"),
]

KEY_PROBLEMS = [
    ("（1）多租户数据隔离与细粒度权限控制。",
     "JWT 中携带 tenant_id 与角色列表，由网关统一鉴权；服务层通过租户拦截器为查询自动附加租户条件；"
     "前端按角色动态生成菜单并做路由守卫，保证平台级角色与租户级角色的权限边界清晰。"),
    ("（2）AI 与业务系统的解耦与安全集成。",
     "AI 服务仅承担计算与生成职责，业务数据由后端通过内网接口按需提供；AI 不可用时主流程自动降级；"
     "对 AI 发起的写操作引入人工确认环节，防止模型幻觉直接修改业务数据。"),
    ("（3）可解释的供需智能匹配。",
     "将向量语义召回与规则多维打分相融合，通过权重建模与最低分阈值过滤保证召回质量，"
     "并由大模型生成自然语言匹配理由，提升推荐结果的可解释性与可信度。"),
    ("（4）外部风险信号与履约数据的整合。",
     "统一接入制裁名单、股权关系、诉讼与行政处罚、天气与航线等外部数据源，"
     "将其与具体供应商和订单关联，触发分级预警并支撑应急处置推演。"),
    ("（5）跨境业务全链路的状态一致性与可追溯性。",
     "针对询盘、报价、授标、合同、交付等环节设计状态机，关键节点自动存证并留存审计日志，保证业务过程可追溯。"),
]

SCHEDULE_ROWS = [
    ("选题与开题", "2026 年 12 月", "调研国内外 SRM 与供应链协同平台现状，完成需求分析，撰写开题报告", "开题报告、文献综述"),
    ("需求分析与总体设计", "2026 年 12 月—2027 年 1 月", "完成用例建模、业务流程分析，以及系统总体架构、数据库与接口设计", "需求规格说明书、概要设计说明书"),
    ("核心功能开发（一）", "2027 年 1 月—2027 年 2 月", "开发平台治理、多租户权限、供应商入驻审核、画像与智能匹配等模块", "可运行的核心功能模块"),
    ("核心功能开发（二）", "2027 年 2 月—2027 年 3 月", "开发询盘报价、比价授标、电子合同、交付履约与风险监控等模块", "业务闭环可运行"),
    ("中期检查", "2027 年 3 月", "按学校要求提交中期检查材料，汇报课题进展", "中期检查报告"),
    ("AI 能力与系统集成测试", "2027 年 3 月—2027 年 4 月", "开发 AI 助手与报价分析等 AI 能力，完成系统集成、联调与测试", "测试用例与测试报告"),
    ("系统部署与论文撰写", "2027 年 4 月", "完成系统部署，撰写毕业设计论文初稿", "论文初稿、部署文档"),
    ("论文修改与答辩", "2027 年 4 月—2027 年 5 月", "根据指导教师意见修改论文，完成查重、定稿与答辩准备", "定稿论文、答辩材料"),
]

REFERENCES = [
    "[1] 张海藩, 牟永敏. 软件工程导论(第 6 版)[M]. 北京: 清华大学出版社, 2013.",
    "[2] 王珊, 萨师煊. 数据库系统概论(第 5 版)[M]. 北京: 高等教育出版社, 2014.",
    "[3] 霍春阳. Vue.js 设计与实现[M]. 北京: 人民邮电出版社, 2022.",
    "[4] Walls C. Spring in Action (6th Edition)[M]. Shelter Island: Manning Publications, 2022.",
    "[5] Fowler M, Lewis J. Microservices: A Definition of This New Architectural Term[EB/OL]. "
    "https://martinfowler.com/articles/microservices.html, 2014.",
    "[6] Sandhu R S, Coyne E J, Feinstein H L, et al. Role-Based Access Control Models[J]. "
    "IEEE Computer, 1996, 29(2): 38-47.",
    "[7] Krebs R, Momm C, Kounev S. Architectural Concerns in Multi-Tenant SaaS Applications[C]"
    "//Proceedings of the 2nd International Conference on Cloud Computing and Services Science "
    "(CLOSER 2012). SciTePress, 2012: 426-431.",
    "[8] Lewis P, Perez E, Piktus A, et al. Retrieval-Augmented Generation for Knowledge-Intensive "
    "NLP Tasks[C]//Advances in Neural Information Processing Systems 33 (NeurIPS 2020). 2020: 9459-9474.",
    "[9] Johnson J, Douze M, Jégou H. Billion-Scale Similarity Search with GPUs[J]. "
    "IEEE Transactions on Big Data, 2021, 7(3): 535-547.",
    "[10] Wang J, Yi X, Guo R, et al. Milvus: A Purpose-Built Vector Data Management System[C]"
    "//Proceedings of the 2021 International Conference on Management of Data (SIGMOD 2021). "
    "ACM, 2021: 2614-2627.",
    "[11] Spring Cloud Alibaba 官方文档[EB/OL]. https://sca.aliyun.com/.",
    "[12] Vue.js 官方文档[EB/OL]. https://cn.vuejs.org/guide/.",
]


def build():
    doc = Document()

    # 页面设置：A4，常规页边距
    sec = doc.sections[0]
    sec.page_width = Cm(21.0)
    sec.page_height = Cm(29.7)
    sec.top_margin = Cm(2.54)
    sec.bottom_margin = Cm(2.54)
    sec.left_margin = Cm(3.0)
    sec.right_margin = Cm(2.6)

    # 默认样式
    normal = doc.styles["Normal"]
    normal.font.name = "Times New Roman"
    normal.font.size = Pt(12)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")

    add_page_number_footer(sec)

    # 标题
    add_para(doc, "毕业设计（论文）任务书", cn_font="黑体", size=22, bold=True,
             indent=False, align=WD_ALIGN_PARAGRAPH.CENTER,
             line_spacing=1.2, space_before=6, space_after=14)

    # 课题信息表
    table = doc.add_table(rows=len(INFO_ROWS), cols=4)
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    col_widths = [Cm(3.0), Cm(5.4), Cm(3.0), Cm(5.4)]
    for row in table.rows:
        prevent_row_split(row)
        for i, cell in enumerate(row.cells):
            cell.width = col_widths[i]

    for r_idx, (c0, c1, c2, c3) in enumerate(INFO_ROWS):
        row = table.rows[r_idx]
        if c2 is None:  # 课题名称行：合并后三列
            merged = row.cells[1].merge(row.cells[2]).merge(row.cells[3])
            set_cell_text(row.cells[0], c0, bold=True)
            set_cell_text(merged, c1, bold=True)
        else:
            set_cell_text(row.cells[0], c0, bold=True)
            set_cell_text(row.cells[1], c1)
            set_cell_text(row.cells[2], c2, bold=True)
            set_cell_text(row.cells[3], c3)

    # 一、课题背景与意义
    add_h1(doc, "一、课题的背景与意义")
    for para in BACKGROUND:
        add_para(doc, para)

    # 二、主要研究内容
    add_h1(doc, "二、主要研究内容")
    for title, detail in RESEARCH_ITEMS:
        add_para(doc, title + detail)

    # 三、基本要求
    add_h1(doc, "三、基本要求")
    for title, detail in REQUIREMENTS:
        add_para(doc, title + detail)

    # 四、拟解决的关键问题
    add_h1(doc, "四、拟解决的关键问题")
    for title, detail in KEY_PROBLEMS:
        add_para(doc, title + detail)

    # 五、进度安排（另起一页，保证表格完整）
    add_h1(doc, "五、进度安排", page_break=True)
    sched = doc.add_table(rows=len(SCHEDULE_ROWS) + 1, cols=4)
    sched.style = "Table Grid"
    sched.alignment = WD_TABLE_ALIGNMENT.CENTER
    sched.autofit = False
    widths = [Cm(3.6), Cm(4.2), Cm(6.4), Cm(3.6)]
    for row in sched.rows:
        prevent_row_split(row)
        for i, cell in enumerate(row.cells):
            cell.width = widths[i]
    set_repeat_header(sched.rows[0])
    headers = ("阶段", "时间", "主要任务", "阶段成果")
    for i, h in enumerate(headers):
        set_cell_text(sched.rows[0].cells[i], h, bold=True)
    for r_idx, values in enumerate(SCHEDULE_ROWS, start=1):
        for c_idx, v in enumerate(values):
            align = WD_ALIGN_PARAGRAPH.CENTER if c_idx in (0, 1) else WD_ALIGN_PARAGRAPH.LEFT
            set_cell_text(sched.rows[r_idx].cells[c_idx], v, align=align)

    # 六、主要参考资料
    add_h1(doc, "六、主要参考资料")
    for ref in REFERENCES:
        add_para(doc, ref, size=10.5, indent=False, align=WD_ALIGN_PARAGRAPH.LEFT,
                 line_spacing=1.3, space_after=2)

    # 七、指导教师意见
    add_h1(doc, "七、指导教师意见")
    box = doc.add_table(rows=1, cols=1)
    box.style = "Table Grid"
    box.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = box.cell(0, 0)
    cell.width = Cm(15.4)
    box.rows[0].height = Cm(8)

    hint = cell.paragraphs[0]
    hint.paragraph_format.space_before = Pt(6)
    run = hint.add_run("（对课题的学术要求、进度要求及其它说明）")
    set_run_font(run, "宋体", "Times New Roman", 10.5)

    for _ in range(7):
        p = cell.add_paragraph()
        p.paragraph_format.space_after = Pt(0)
        set_run_font(p.add_run(""), "宋体", "Times New Roman", 12)

    sign = cell.add_paragraph()
    sign.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    sign.paragraph_format.space_before = Pt(6)
    set_run_font(sign.add_run("指导教师（签字）：__________________"), "宋体", "Times New Roman", 12)
    date = cell.add_paragraph()
    date.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    set_run_font(date.add_run("年        月        日"), "宋体", "Times New Roman", 12)

    out_path = os.path.join(os.getcwd(), OUTPUT)
    doc.save(out_path)
    print(f"已生成: {out_path}")


if __name__ == "__main__":
    build()
