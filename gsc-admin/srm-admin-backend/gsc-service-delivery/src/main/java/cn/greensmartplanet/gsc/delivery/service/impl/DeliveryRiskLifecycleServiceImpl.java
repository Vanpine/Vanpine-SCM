package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.enums.MaterialCriticalLevel;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.dto.Create8dDTO;
import cn.greensmartplanet.gsc.delivery.dto.CreateLogisticsEventDTO;
import cn.greensmartplanet.gsc.delivery.dto.Submit8dDTO;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.Delivery8dReport;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryLogisticsEvent;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRiskFactor;
import cn.greensmartplanet.gsc.delivery.entity.Notification;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.enums.LogisticsEventType;
import cn.greensmartplanet.gsc.delivery.enums.RiskLevel;
import cn.greensmartplanet.gsc.delivery.enums.RiskStatus;
import cn.greensmartplanet.gsc.delivery.enums.RiskType;
import cn.greensmartplanet.gsc.delivery.mapper.Delivery8dReportMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryLogisticsEventMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskFactorMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskMapper;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRiskLifecycleService;
import cn.greensmartplanet.gsc.delivery.service.NotificationService;
import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import cn.greensmartplanet.gsc.delivery.mq.producer.WeatherAdvicePublisher;
import cn.greensmartplanet.gsc.delivery.support.DeliveryTraceSupport;
import cn.greensmartplanet.gsc.delivery.support.WeatherAnomaly;
import cn.greensmartplanet.gsc.delivery.support.WeatherRiskEvaluator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交付风险生命周期服务实现
 * <p>
 * 物流事件 → 风险识别 → 影响判断（确定性规则）：
 * 履约阈值读合同交付规则；天气阈值在 {@link WeatherRiskEvaluator}。
 * 天气异常落库后发 MQ，后台补 AI 处置建议（风险先出、建议后补）。
 * 风险与交付单状态机分离，不改 delivery.status。
 * 定时扫描 scanRisks 批量预取候选交付单/合同/物流事件/天气地点一次，消除逐单 N+1。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryRiskLifecycleServiceImpl implements DeliveryRiskLifecycleService {

    private final DeliveryRiskMapper riskMapper;
    private final DeliveryRiskFactorMapper factorMapper;
    private final DeliveryLogisticsEventMapper eventMapper;
    private final DeliveryMapper deliveryMapper;
    // 8D 整改报告（供应商问题整改，采购方发起/审核）
    private final Delivery8dReportMapper report8dMapper;
    // 站内通知（8D 发起/提交/审核结果通知双方）
    private final NotificationService notificationService;
    // 合同规则字段归交易域，Feign 查询（风险阈值全部来自合同交付规则）
    private final ContractClient contractClient;
    // 天气异常：交货地 + 最新物流点批量评估（Open-Meteo，失败当无异常）
    private final WeatherRiskEvaluator weatherRiskEvaluator;
    private final DeliveryTraceSupport traceSupport;
    private final WeatherAdvicePublisher weatherAdvicePublisher;

    // ==================== 物流事件 ====================

    /** 录入物流事件：校验归属 → 落库 → 触发一次风险识别 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordEvent(Long tenantId, Long userId, CreateLogisticsEventDTO dto) {
        // 事件类型必须是标准化 9 类之一，未知类型直接拒绝（防脏数据进时间线）
        if (LogisticsEventType.fromCode(dto.getEventType()) == null) {
            throw new BizException(ErrorCode.RISK_EVENT_TYPE_INVALID);
        }
        Delivery d = deliveryMapper.selectById(dto.getDeliveryId());
        if (d == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        // 仅供应商可录物流节点：货是供应商发的，物理节点由发货方/承运人上报；
        // 采购方开放录入会绕过"验收前置需到达事件"的约束（自录到达即可验收）
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 仅未闭环交付单允许录入物流事件（已验收/已退回无再录物流事件的意义）
        if (DeliveryStatus.ACCEPTED.getCode().equals(d.getStatus())
                || DeliveryStatus.RETURNED.getCode().equals(d.getStatus())) {
            throw new BizException(ErrorCode.DELIVERY_STATUS_INVALID);
        }
        DeliveryLogisticsEvent event = DeliveryLogisticsEvent.builder()
                .deliveryId(dto.getDeliveryId())
                .eventType(dto.getEventType())
                .eventTime(dto.getEventTime())
                .estimatedArrivalTime(dto.getEstimatedArrivalTime())
                .location(dto.getLocation())
                .remark(dto.getRemark())
                .source("manual")
                .operatorTenantId(tenantId)
                .operatorUserId(userId)
                .build();
        eventMapper.insert(event);
        log.info("物流事件录入: deliveryId={}, eventType={}, operator={}",
                dto.getDeliveryId(), dto.getEventType(), tenantId);
        // 立即针对该交付单识别一次风险
        scanDelivery(d.getId());
    }

    // ==================== 定时扫描（批量预取，消除 N+1） ====================

    /**
     * 定时扫描：按合同规则识别/升级未闭环交付单的风险
     * <p>
     * 批量预取一次：候选交付单（selectBatchIds）、合同（getContractByIds）、
     * 最新事件/ETA（selectLatestByDeliveryIds）、清关滞留交付单集（selectDeliveryIdsWithEventType），
     * 再逐单组装评估，消除原实现循环内逐单 Feign + 逐单 DB 的 N+1。
     */
    @Override
    public void scanRisks() {
        List<Long> deliveryIds = riskMapper.selectScanCandidates();
        if (deliveryIds.isEmpty()) {
            return;
        }
        // 1. 批量预取候选交付单实体（selectBatchIds 一次取回，替代逐单 selectById）
        List<Delivery> deliveries = deliveryMapper.selectBatchIds(deliveryIds);
        // 1a. 过滤掉无合同关联的脏数据，组装 id→实体 Map 供后续按单取用
        Map<Long, Delivery> deliveryMap = deliveries.stream()
                .filter(d -> d != null && d.getContractId() != null)
                .collect(Collectors.toMap(Delivery::getId, Function.identity()));
        if (deliveryMap.isEmpty()) {
            return;
        }
        // 2. 批量预取合同（交易域 Feign 批量，替代逐单 getContractById）
        // 2a. 收集候选交付单涉及的去重合同ID
        List<Long> contractIds = deliveryMap.values().stream()
                .map(Delivery::getContractId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        // 2b. 一次性 Feign 批量取回合同，组装 contractId→DTO Map
        Map<Long, ContractInfoDTO> contractMap = contractIds.isEmpty() ? Map.of()
                : contractClient.getContractByIds(contractIds).getData();
        // 3. 批量预取最新物流事件 / 最新 ETA（替代逐单 selectLatestByDeliveryId）
        // 3a. 每单最新一条物流事件（清关/停滞等判定用）
        Map<Long, DeliveryLogisticsEvent> latestMap = eventMapper.selectLatestByDeliveryIds(deliveryIds).stream()
                .collect(Collectors.toMap(DeliveryLogisticsEvent::getDeliveryId, Function.identity()));
        // 3b. 每单最新一条带 ETA 的事件（晚于交期判定用）
        Map<Long, DeliveryLogisticsEvent> latestEtaMap = eventMapper.selectLatestEtaByDeliveryIds(deliveryIds).stream()
                .collect(Collectors.toMap(DeliveryLogisticsEvent::getDeliveryId, Function.identity()));
        // 4. 批量判定清关滞留：存在 customs_hold 事件的交付单集合（替代逐单 countByDeliveryAndType）
        Set<Long> customsHoldIds = new HashSet<>(
                eventMapper.selectDeliveryIdsWithEventType(deliveryIds, LogisticsEventType.CUSTOMS_HOLD.getCode()));

        // 5. 批量评估天气：交货地 + 最新物流地点去重后打 WeatherClient，禁止循环内逐单取数
        Set<String> weatherPlaces = new HashSet<>();
        for (Delivery d : deliveryMap.values()) {
            if (StringUtils.hasText(d.getDeliveryPlace())) {
                weatherPlaces.add(d.getDeliveryPlace().trim());
            }
            DeliveryLogisticsEvent latest = latestMap.get(d.getId());
            if (latest != null && StringUtils.hasText(latest.getLocation())) {
                weatherPlaces.add(latest.getLocation().trim());
            }
        }
        Map<String, WeatherAnomaly> weatherByPlace = weatherRiskEvaluator.assessPlaces(weatherPlaces);

        // 6. 逐单评估（合同/物流/天气均已预取，评估过程不再打外部天气）
        int scanned = 0;
        for (Delivery d : deliveries) {
            if (d == null || d.getContractId() == null) {
                continue;
            }
            ContractInfoDTO contract = contractMap.get(d.getContractId());
            // 仅 signed 合同参与风险扫描（原 SQL JOIN contract 的过滤语义，改由 Feign 批量判断）
            if (contract == null || !ContractStatus.SIGNED.getCode().equals(contract.getStatus())) {
                continue;
            }
            evaluateDelivery(d, contract,
                    latestMap.get(d.getId()), latestEtaMap.get(d.getId()),
                    customsHoldIds.contains(d.getId()),
                    resolveWeather(d, latestMap.get(d.getId()), weatherByPlace));
            scanned++;
        }
        log.info("风险扫描完成, 候选交付单数={}, 已评估={}", deliveryIds.size(), scanned);
    }

    /**
     * 对单个交付单识别风险：评估规则 → 一单一主表（复用）+ 因子轨迹（类型切换时归档旧因子）。
     * 供物流事件录入（recordEvent）单条触发。
     */
    private void scanDelivery(Long deliveryId) {
        Delivery d = deliveryMapper.selectById(deliveryId);
        if (d == null || d.getContractId() == null) {
            return;
        }
        ContractInfoDTO c = contractClient.getContractById(d.getContractId()).getData();
        if (c == null) {
            return;
        }
        DeliveryLogisticsEvent latest = eventMapper.selectLatestByDeliveryId(deliveryId);
        DeliveryLogisticsEvent latestEta = eventMapper.selectLatestEtaByDeliveryId(deliveryId);
        boolean hasCustomsHold = eventMapper.countByDeliveryAndType(
                deliveryId, LogisticsEventType.CUSTOMS_HOLD.getCode()) > 0;
        // 单条触发也先评估两个监控点，再交给同一套落库逻辑
        WeatherAnomaly weather = resolveWeather(d, latest, weatherRiskEvaluator.assessPlaces(
                java.util.Arrays.asList(d.getDeliveryPlace(),
                        latest == null ? null : latest.getLocation())));
        evaluateDelivery(d, c, latest, latestEta, hasCustomsHold, weather);
    }

    /**
     * 从预取结果里挑本单更严重的天气异常：交货地 + 最新物流点。
     */
    private WeatherAnomaly resolveWeather(Delivery d, DeliveryLogisticsEvent latest,
                                          Map<String, WeatherAnomaly> weatherByPlace) {
        WeatherAnomaly atPlace = lookupWeather(d.getDeliveryPlace(), weatherByPlace);
        WeatherAnomaly atNode = latest == null ? null : lookupWeather(latest.getLocation(), weatherByPlace);
        return weatherRiskEvaluator.pickWorse(atPlace, atNode);
    }

    private WeatherAnomaly lookupWeather(String place, Map<String, WeatherAnomaly> weatherByPlace) {
        if (!StringUtils.hasText(place) || weatherByPlace == null) {
            return null;
        }
        return weatherByPlace.get(place.trim());
    }

    /** 单个交付单风险评估核心（扫描/录入共用）：更新主表风险行 + 因子轨迹 */
    private void evaluateDelivery(Delivery d, ContractInfoDTO c,
                                  DeliveryLogisticsEvent latest, DeliveryLogisticsEvent latestEta,
                                  boolean hasCustomsHold, WeatherAnomaly weather) {
        Long deliveryId = d.getId();
        String riskType = evaluateRiskType(d, c, latest, latestEta, hasCustomsHold, weather);
        DeliveryRisk existing = riskMapper.selectByDeliveryId(deliveryId);

        // 无风险：风险条件消失 → 未关闭风险自动缓解 + 活跃因子关闭
        if (riskType == null) {
            if (existing != null) {
                if (isOpen(existing.getRiskStatus())) {
                    riskMapper.updateRiskState(existing.getId(), existing.getRiskLevel(),
                            RiskStatus.MITIGATED.getCode(), existing.getEstimatedArrivalTime(),
                            existing.getEvidence(), existing.getDueTime());
                }
                factorMapper.closeActiveByRiskId(existing.getId());
            }
            return;
        }

        LocalDateTime eta = latestEta != null ? latestEta.getEstimatedArrivalTime() : null;
        String level = evaluateLevel(d, c, riskType, eta, weather);
        int score = calcRiskScore(d, c, riskType, level, eta);
        String evidence = buildEvidence(riskType, d, c, weather);
        String status = MaterialCriticalLevel.HIGH.getCode().equals(level)
                ? RiskStatus.HIGH.getCode() : RiskStatus.WARNED.getCode();

        // 首次命中：插入主表 + 第一个 active 因子
        if (existing == null) {
            DeliveryRisk risk = DeliveryRisk.builder()
                    .deliveryId(deliveryId)
                    .contractId(d.getContractId())
                    .riskType(riskType)
                    .riskLevel(level)
                    .riskStatus(status)
                    .estimatedArrivalTime(eta)
                    .promisedDeliveryDate(d.getConfirmedDeliveryDate() != null
                            ? d.getConfirmedDeliveryDate() : d.getDeliveryDate())
                    .productionNeedDate(c.getProductionNeedDate())
                    .impactQty(d.getQuantity())
                    .impactAmount(d.getAmount())
                    .riskScore(score)
                    .evidence(evidence)
                    .firstDetectedTime(LocalDateTime.now())
                    .build();
            try {
                riskMapper.insert(risk);
            } catch (DuplicateKeyException e) {
                // 并发实例刚插进去（uk_delivery_active 唯一索引挡住重复行）：重查后落到下面
                // 「已有主表行」分支。不能让异常冒泡——evaluateDelivery 在 scanRisks 的 for 循环里，
                // 冒泡会中断整轮扫描，剩余交付单本轮全部跳过。
                log.info("风险行已被并发实例创建，转为更新: deliveryId={}", deliveryId);
                existing = riskMapper.selectByDeliveryId(deliveryId);
                if (existing == null) {
                    throw e; // 查不到说明不是并发冲突，是真实异常，继续抛出
                }
            }
            if (existing == null) {
                insertActiveFactor(risk.getId(), deliveryId, riskType, level, eta, score, evidence, risk.getFirstDetectedTime());
                // 天气风险首次出现才通知：同一单后续扫描只更新主表，避免 5 分钟刷屏
                notifyWeatherIfNew(d, weather, riskType, risk.getId(), eta);
                log.info("风险生成: deliveryId={}, type={}, level={}, status={}",
                        deliveryId, riskType, level, status);
                return;
            }
        }

        // 已有主表行：类型切换时归档旧因子、开新因子
        boolean typeChanged = !riskType.equals(existing.getRiskType());
        if (typeChanged) {
            factorMapper.closeActiveByRiskId(existing.getId());
            insertActiveFactor(existing.getId(), deliveryId, riskType, level, eta, score, evidence, LocalDateTime.now());
        }

        boolean reopening = RiskStatus.CLOSED.getCode().equals(existing.getRiskStatus())
                || RiskStatus.MITIGATED.getCode().equals(existing.getRiskStatus());

        String newLevel;
        String newStatus;
        LocalDateTime newFirstDetected;
        if (reopening) {
            // 复用同一行重启：closed/mitigated → 当前状态，刷新首测时间
            newLevel = level;
            newStatus = status;
            newFirstDetected = LocalDateTime.now();
            notifyWeatherIfNew(d, weather, riskType, existing.getId(), eta);
        } else if (typeChanged) {
            // 类型切换：等级取新类型，处置状态保留
            newLevel = level;
            newStatus = existing.getRiskStatus();
            newFirstDetected = existing.getFirstDetectedTime();
            publishWeatherAdvice(d, weather, riskType, existing.getId(), eta);
        } else {
            // 同类型：等级可升，high 状态升级，其余保留
            newLevel = levelUp(level, existing.getRiskLevel()) ? level : existing.getRiskLevel();
            newStatus = MaterialCriticalLevel.HIGH.getCode().equals(newLevel)
                    ? RiskStatus.HIGH.getCode() : existing.getRiskStatus();
            newFirstDetected = existing.getFirstDetectedTime();
        }

        riskMapper.updateCurrent(existing.getId(), riskType, newLevel, newStatus,
                eta, evidence, score, newFirstDetected);
        log.info("风险更新: id={}, type={}, level={}, status={}", existing.getId(), riskType, newLevel, newStatus);
    }

    /** 是否未关闭（含观察/预警/高风险/处置中） */
    private boolean isOpen(String riskStatus) {
        return RiskStatus.ACTIVE.getCode().equals(riskStatus)
                || RiskStatus.WARNED.getCode().equals(riskStatus)
                || RiskStatus.HIGH.getCode().equals(riskStatus)
                || RiskStatus.DISPOSING.getCode().equals(riskStatus);
    }

    /** 新增一条 active 因子 */
    private void insertActiveFactor(Long riskId, Long deliveryId, String riskType, String level,
                                    LocalDateTime eta, int score, String evidence, LocalDateTime firstDetected) {
        factorMapper.insert(DeliveryRiskFactor.builder()
                .riskId(riskId)
                .deliveryId(deliveryId)
                .riskType(riskType)
                .riskLevel(level)
                .factorStatus("active")
                .estimatedArrivalTime(eta)
                .riskScore(score)
                .evidence(evidence)
                .firstDetectedTime(firstDetected)
                .closedTime(null)
                .build());
    }

    /**
     * 按合同规则评估风险类型，返回 null 表示当前无风险
     * <p>hasCustomsHold 由扫描批量预取（一次 IN 查询），替代逐单 count。
     */
    private String evaluateRiskType(Delivery d, ContractInfoDTO c,
                                    DeliveryLogisticsEvent latest, DeliveryLogisticsEvent latestEta,
                                    boolean hasCustomsHold, WeatherAnomaly weather) {
        LocalDate today = LocalDate.now();

        // 1. 未按计划发运：已过约定最晚发运日仍未发货
        LocalDate plannedShipDate = c.getPlannedShipDate();
        if (plannedShipDate != null && !plannedShipDate.isAfter(today)
                && DeliveryStatus.PENDING_SHIPMENT.getCode().equals(d.getStatus())) {
            return RiskType.LATE_SHIPMENT.getCode();
        }

        // 2. 清关阻塞：存在 customs_hold 且最新事件仍是 customs_hold（未清关）
        if (hasCustomsHold && latest != null && LogisticsEventType.CUSTOMS_HOLD.getCode().equals(latest.getEventType())) {
            return RiskType.CLEARANCE_HOLD.getCode();
        }

        // 3. 预计晚于交期：ETA 晚于 考核基准（承诺优先）+ 允许延期
        LocalDateTime eta = latestEta != null ? latestEta.getEstimatedArrivalTime() : null;
        LocalDate effectiveDate = d.getConfirmedDeliveryDate() != null
                ? d.getConfirmedDeliveryDate() : d.getDeliveryDate();
        if (eta != null && effectiveDate != null) {
            LocalDate allowedDeadline = effectiveDate.plusDays(
                    c.getAllowedDelayDays() != null ? c.getAllowedDelayDays() : 0);
            if (eta.toLocalDate().isAfter(allowedDeadline)) {
                return RiskType.ETA_DELAY.getCode();
            }
        }

        // 4. 物流停滞：已发货但最新事件距今超 exception_notice_hours
        if (!DeliveryStatus.PENDING_SHIPMENT.getCode().equals(d.getStatus()) && latest != null) {
            int hours = c.getExceptionNoticeHours() != null ? c.getExceptionNoticeHours() : 24;
            long elapsedHours = Duration.between(latest.getEventTime(), LocalDateTime.now()).toHours();
            if (elapsedHours > hours) {
                return RiskType.LOGISTICS_STAGNATION.getCode();
            }
        }

        // 5. 数量风险：合同不允许部分交付且实发少于应发
        if (Integer.valueOf(0).equals(c.getPartialDeliveryAllowed())
                && d.getShipQty() != null && d.getQuantity() != null
                && d.getShipQty() < d.getQuantity()) {
            return RiskType.QUANTITY_SHORTAGE.getCode();
        }

        // 6. 天气异常：履约类风险优先，都不命中才用天气当主类型（一单一主表）
        if (weather != null) {
            return RiskType.WEATHER_DISRUPTION.getCode();
        }

        return null;
    }

    /** 评估风险等级：天气用气象阈值；其余看是否会断供 */
    private String evaluateLevel(Delivery d, ContractInfoDTO c, String riskType, LocalDateTime eta,
                                 WeatherAnomaly weather) {
        if (RiskType.WEATHER_DISRUPTION.getCode().equals(riskType) && weather != null) {
            return weather.getLevel();
        }
        // 断供升级：预计到货晚于生产需求日期，且库存覆盖天数不足延期
        if (eta != null && c.getProductionNeedDate() != null) {
            long delayDays = Duration.between(c.getProductionNeedDate().atStartOfDay(), eta).toDays();
            int coverDays = c.getInventoryCoverDays() != null ? c.getInventoryCoverDays() : 0;
            if (delayDays > coverDays) {
                return RiskLevel.HIGH.getCode();
            }
        }
        // 关键物料 + 高风险类型组合，一期简化：默认 medium
        return RiskLevel.MEDIUM.getCode();
    }

    /** 等级升高判断：high > medium > low */
    private boolean levelUp(String newLevel, String oldLevel) {
        return rank(newLevel) > rank(oldLevel);
    }

    private int rank(String level) {
        if (RiskLevel.HIGH.getCode().equals(level)) return 3;
        if (RiskLevel.MEDIUM.getCode().equals(level)) return 2;
        if (RiskLevel.LOW.getCode().equals(level)) return 1;
        return 0;
    }

    /** 透明风险分：发生概率 × 影响程度 × 紧迫程度（各 0-100，归一化到 0-100） */
    private int calcRiskScore(Delivery d, ContractInfoDTO c, String riskType, String level, LocalDateTime eta) {
        // 发生概率：风险类型基础分（清关/停滞高，未发运中，数量低）
        int prob = switch (riskType) {
            case "clearance_hold", "logistics_stagnation" -> 80;
            case "eta_delay" -> 70;
            case "late_shipment" -> 60;
            case "weather_disruption" -> 75;
            case "quantity_shortage" -> 50;
            default -> 60;
        };
        // 影响程度：物料关键等级（high=100/medium=70/low=40/未设=50）
        int impact = switch (c.getMaterialCriticalLevel() == null ? "low" : c.getMaterialCriticalLevel()) {
            case "high" -> 100;
            case "medium" -> 70;
            default -> 40;
        };
        // 紧迫程度：已超生产需求日期=100，否则按距交期远近
        int urgency = 60;
        if (eta != null && c.getProductionNeedDate() != null
                && eta.toLocalDate().isAfter(c.getProductionNeedDate())) {
            urgency = 100;
        } else if (d.getDeliveryDate() != null && !d.getDeliveryDate().isAfter(LocalDate.now())) {
            urgency = 80;
        }
        return prob * impact * urgency / 10000;
    }

    /** 组装触发证据（详情页展示"为什么"），有序拼接为 k=v 文本，类型/等级已转中文 */
    private String buildEvidence(String riskType, Delivery d, ContractInfoDTO c, WeatherAnomaly weather) {
        if (RiskType.WEATHER_DISRUPTION.getCode().equals(riskType) && weather != null) {
            return weather.getEvidence();
        }
        Map<String, String> riskTypeMap = Map.of(
                "late_shipment", "未按计划发运",
                "logistics_stagnation", "物流停滞",
                "eta_delay", "预计晚于交期",
                "clearance_hold", "清关阻塞",
                "quantity_shortage", "数量不足",
                "weather_disruption", "天气异常",
                "other", "其他异常");
        String materialLevel = c.getMaterialCriticalLevel() == null ? "未设置"
                : MaterialCriticalLevel.HIGH.getCode().equals(c.getMaterialCriticalLevel()) ? "高"
                : MaterialCriticalLevel.MEDIUM.getCode().equals(c.getMaterialCriticalLevel()) ? "中"
                : MaterialCriticalLevel.LOW.getCode().equals(c.getMaterialCriticalLevel()) ? "低" : c.getMaterialCriticalLevel();
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("风险类型", riskTypeMap.getOrDefault(riskType, riskType));
        m.put("合同交期", d.getDeliveryDate() != null ? d.getDeliveryDate().toString() : "-");
        m.put("约定最晚发运", c.getPlannedShipDate() != null ? c.getPlannedShipDate().toString() : "-");
        m.put("允许延期天数", String.valueOf(c.getAllowedDelayDays() != null ? c.getAllowedDelayDays() : 0));
        m.put("物料关键等级", materialLevel);
        m.put("库存覆盖天数", String.valueOf(c.getInventoryCoverDays() != null ? c.getInventoryCoverDays() : 0));
        return m.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    // ==================== 8D 整改 ====================

    /** 采购方发起 8D 整改：仅未关闭风险可发起，一风险一张（uk_risk_id 防重），通知供应商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create8d(Long riskId, Long tenantId, Long userId, Create8dDTO dto) {
        // 1. 校验风险存在 + 采购方身份 + 风险未关闭
        DeliveryRisk r = getRisk(riskId, tenantId);
        Delivery d = deliveryMapper.selectById(r.getDeliveryId());
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (RiskStatus.CLOSED.getCode().equals(r.getRiskStatus())
                || RiskStatus.MITIGATED.getCode().equals(r.getRiskStatus())) {
            throw new BizException(ErrorCode.RISK_STATUS_INVALID);
        }
        // 2. 一风险一张 8D
        if (report8dMapper.selectByRiskId(riskId) != null) {
            throw new BizException(ErrorCode.RISK_8D_EXISTS);
        }
        Delivery8dReport report = Delivery8dReport.builder()
                .id(IdWorker.getId())
                .riskId(riskId)
                .status("pending")
                .deadline(dto.getDeadline())
                .createBy(userId)
                .build();
        report8dMapper.insert(report);
        // 3. 通知供应商填写
        notificationService.notify(Notification.builder()
                .type("DELIVERY_8D_CREATE")
                .title("请填写 8D 整改报告")
                .content("采购方对交付风险【" + riskTypeCn(r) + "】发起了 8D 整改，请在 "
                        + dto.getDeadline() + " 前完成填写")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("发起8D整改: riskId={}, reportId={}, deadline={}", riskId, report.getId(), dto.getDeadline());
        return report.getId();
    }

    /** 供应商提交 8D 报告：D1-D8 全必填（DTO 校验），pending/rejected 可提交，通知采购方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit8d(Long reportId, Long tenantId, Submit8dDTO dto) {
        Delivery8dReport report = getReport(reportId);
        DeliveryRisk r = riskMapper.selectById(report.getRiskId());
        Delivery d = deliveryMapper.selectById(r.getDeliveryId());
        // 仅供应商可提交
        if (!tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (report8dMapper.submit(reportId, dto.getD1Team(), dto.getD2Desc(), dto.getD3Containment(),
                dto.getD4RootCause(), dto.getD5Corrective(), dto.getD6Verification(),
                dto.getD7Prevention(), dto.getD8Summary()) == 0) {
            throw new BizException(ErrorCode.RISK_8D_STATUS_INVALID);
        }
        // 通知采购方审核
        notificationService.notify(Notification.builder()
                .type("DELIVERY_8D_SUBMIT")
                .title("8D 整改报告已提交")
                .content("供应商已提交交付风险【" + riskTypeCn(r) + "】的 8D 整改报告，请审核")
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(tenantId)
                .fromUserId(null)
                .build());
        log.info("提交8D报告: reportId={}, riskId={}", reportId, report.getRiskId());
    }

    /** 采购方审核通过 8D：submitted → approved，通知供应商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve8d(Long reportId, Long tenantId, Long userId) {
        Delivery8dReport report = getReport(reportId);
        DeliveryRisk r = riskMapper.selectById(report.getRiskId());
        Delivery d = deliveryMapper.selectById(r.getDeliveryId());
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (report8dMapper.approve(reportId, userId) == 0) {
            throw new BizException(ErrorCode.RISK_8D_STATUS_INVALID);
        }
        notificationService.notify(Notification.builder()
                .type("DELIVERY_8D_APPROVE")
                .title("8D 整改报告已通过")
                .content("交付风险【" + riskTypeCn(r) + "】的 8D 整改报告已审核通过")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("8D审核通过: reportId={}, riskId={}", reportId, report.getRiskId());
    }

    /** 采购方打回 8D：submitted → rejected，打回原因必填，通知供应商修改重提 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject8d(Long reportId, Long tenantId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BizException(ErrorCode.RISK_8D_REJECT_REASON_REQUIRED);
        }
        Delivery8dReport report = getReport(reportId);
        DeliveryRisk r = riskMapper.selectById(report.getRiskId());
        Delivery d = deliveryMapper.selectById(r.getDeliveryId());
        if (!tenantId.equals(d.getBuyerTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        if (report8dMapper.reject(reportId, userId, reason.trim()) == 0) {
            throw new BizException(ErrorCode.RISK_8D_STATUS_INVALID);
        }
        notificationService.notify(Notification.builder()
                .type("DELIVERY_8D_REJECT")
                .title("8D 整改报告被打回")
                .content("交付风险【" + riskTypeCn(r) + "】的 8D 报告被打回：" + reason.trim() + "，请修改后重新提交")
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .fromTenantId(tenantId)
                .fromUserId(userId)
                .build());
        log.info("8D打回: reportId={}, riskId={}, reason={}", reportId, report.getRiskId(), reason.trim());
    }

    // ==================== 私有工具 ====================

    /** 查 8D 报告并校验存在 */
    private Delivery8dReport getReport(Long reportId) {
        Delivery8dReport report = report8dMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.RISK_8D_NOT_FOUND);
        }
        return report;
    }

    /** 查风险并校验归属（采购方或供应方）——写侧私有副本 */
    private DeliveryRisk getRisk(Long id, Long tenantId) {
        DeliveryRisk r = riskMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.RISK_NOT_FOUND);
        }
        Delivery d = deliveryMapper.selectById(r.getDeliveryId());
        if (d == null) {
            throw new BizException(ErrorCode.RISK_NOT_FOUND);
        }
        if (!tenantId.equals(d.getBuyerTenantId()) && !tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return r;
    }

    /** 风险类型中文（通知文案用） */
    private String riskTypeCn(DeliveryRisk r) {
        return switch (r.getRiskType() == null ? "" : r.getRiskType()) {
            case "late_shipment" -> "未按计划发运";
            case "logistics_stagnation" -> "物流停滞";
            case "eta_delay" -> "预计晚于交期";
            case "clearance_hold" -> "清关阻塞";
            case "quantity_shortage" -> "数量不足";
            case "weather_disruption" -> "天气异常";
            default -> "其他异常";
        };
    }

    /**
     * 天气风险首次出现或缓解后复发才通知双方；扫描更新同类型不重复发。
     */
    private void notifyWeatherIfNew(Delivery d, WeatherAnomaly weather, String riskType,
                                    Long riskId, LocalDateTime eta) {
        if (weather == null || !RiskType.WEATHER_DISRUPTION.getCode().equals(riskType)) {
            return;
        }
        publishWeatherAdvice(d, weather, riskType, riskId, eta);
        String content = "交付单【" + d.getDeliveryNo() + "】监测到天气异常：" + weather.getSummary()
                + "，可能影响在途运输，请评估是否改期、绕行或加固包装。";
        notificationService.notify(Notification.builder()
                .type("DELIVERY_WEATHER_WARNING")
                .title("交付天气预警")
                .content(content)
                .receiverTenantId(d.getBuyerTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .build());
        notificationService.notify(Notification.builder()
                .type("DELIVERY_WEATHER_WARNING")
                .title("交付天气预警")
                .content(content)
                .receiverTenantId(d.getSupplierTenantId())
                .relatedType("delivery")
                .relatedId(d.getId())
                .build());
        traceSupport.addTrace(d.getId(), "weather_warning", "天气预警",
                null, null, weather.getSummary());
    }

    /** 天气风险落库后丢建议任务；扫描主链路不等模型 */
    private void publishWeatherAdvice(Delivery d, WeatherAnomaly weather, String riskType,
                                      Long riskId, LocalDateTime eta) {
        if (weather == null || riskId == null || !RiskType.WEATHER_DISRUPTION.getCode().equals(riskType)) {
            return;
        }
        weatherAdvicePublisher.publish(WeatherAdvicePayload.builder()
                .riskId(riskId)
                .deliveryId(d.getId())
                .deliveryNo(d.getDeliveryNo())
                .place(weather.getPlace())
                .evidence(weather.getEvidence())
                .summary(weather.getSummary())
                .level(weather.getLevel())
                .shipCompany(d.getShipCompany())
                .deliveryPlace(d.getDeliveryPlace())
                .eta(eta == null ? null : eta.toString())
                .promisedDate(d.getConfirmedDeliveryDate() != null
                        ? d.getConfirmedDeliveryDate().toString()
                        : (d.getDeliveryDate() == null ? null : d.getDeliveryDate().toString()))
                .impactQty(d.getQuantity())
                .build());
    }
}
