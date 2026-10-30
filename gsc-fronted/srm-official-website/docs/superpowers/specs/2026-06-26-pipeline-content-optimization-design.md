# Design Doc: 全球直采流程内容优化

## Date
2026-06-26

## Goal
优化 SmartProcurement.vue 中「寻源 → 询盘 → 比价 → 合同」四个流程步骤的文案、结构和命名。

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| 第四步「合同管理」→「智能签约」 | 与前三级组成完整动作链，与 Hero 副标题一致 |
| 比价 6 条 → 4 条 | 合并冗余，控制每步 3-4 条保持节奏均衡 |
| 保持能力描述风格 | B2B 采购决策者需要专业信任感，不滑向营销腔 |
| 每条精炼到一行 | 降低扫描负担，动宾结构开头 |

## Content Changes

### Step 1: 智能寻源
- 资质交叉验证 → "一键交叉验证"
- 尽调列表 → 用顿号简化串联
- 最后一则加"异常主动预警"给用户安全感

### Step 2: 自动询盘
- 明确 "RFP/RFQ" 类别
- "点对点精准分发"替代原来的表达
- "自动归档留痕"补全合规闭环

### Step 3: 智能比价（6→4）
- 历史价格与 TCO 合并为一条
- 隐含成本与异常报价合并
- 风险偏好合并入 ROI 看板决策
- 保持产能·交期·成本推演独立

### Step 4: 智能签约（更名）
- 标题「合同管理」→「智能签约」
- subtitle "Contract Management" → "Smart Contracting"
- "核查"→"审查"更法律场景化
- 履约监控保留（签约不只是签字，含履约）

## Files Affected
- `src/views/SmartProcurement.vue` — pipelineStages 数组

## No Changes
- Hero 副标题已与"签约"一致，无需修改
- 图标（Search / MessageSquare / Scale / FileText）保持不变
- 图片保持不变
