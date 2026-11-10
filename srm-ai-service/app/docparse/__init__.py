"""统一文档解析层（docparse）。

按职责分层：
  sniff    —— 真实类型识别（魔数 / OOXML 内部结构 / 文本启发式）
  adapters —— 各格式解析器（复用 services 下现有实现）
  clean    —— 页眉页脚等清洗
  quality  —— 质量门禁与失败原因码
  pipeline —— 解析 → 清洗 → 门禁 的统一入口
"""
