package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryTrace;
import cn.greensmartplanet.gsc.delivery.projection.ContractAllocationRow;
import cn.greensmartplanet.gsc.delivery.projection.PerformanceStatRow;
import cn.greensmartplanet.gsc.delivery.projection.ShipmentPlanRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 交付单 Mapper
 *
 * @author gsc
 */
public interface DeliveryMapper extends BaseMapper<Delivery> {

    /** 采购方分页查交付单 */
    List<Delivery> selectByBuyer(@Param("tenantId") Long tenantId,
                                 @Param("statuses") List<String> statuses,
                                 @Param("keyword") String keyword,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /** 采购方统计 */
    long countByBuyer(@Param("tenantId") Long tenantId,
                      @Param("statuses") List<String> statuses,
                      @Param("keyword") String keyword);

    /** 供应方分页查交付单 */
    List<Delivery> selectBySupplier(@Param("tenantId") Long tenantId,
                                    @Param("statuses") List<String> statuses,
                                    @Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /** 供应方统计 */
    long countBySupplier(@Param("tenantId") Long tenantId,
                         @Param("statuses") List<String> statuses,
                         @Param("keyword") String keyword);

    /** 按ID查单条 */
    Delivery selectById(@Param("id") Long id);

    /** 插入交付单 */
    int insert(Delivery delivery);

    /** 供应商发货：pending_shipment/returned → shipped，填物流信息清退回原因 */
    int ship(@Param("id") Long id,
             @Param("tenantId") Long tenantId,
             @Param("shipCompany") String shipCompany,
             @Param("shipTrackingNo") String shipTrackingNo,
             @Param("shipQty") Integer shipQty,
             @Param("shipDate") LocalDate shipDate,
             @Param("shipAttachmentKey") String shipAttachmentKey,
             @Param("updateBy") Long updateBy);

    /** 采购商收货：shipped → received */
    int receive(@Param("id") Long id, @Param("tenantId") Long tenantId,
                @Param("receiveQty") Integer receiveQty,
                @Param("receiveDiffRemark") String receiveDiffRemark,
                @Param("receiveAttachmentKey") String receiveAttachmentKey,
                @Param("updateBy") Long updateBy);

    /** 采购商验收合格：received → accepted */
    int accept(@Param("id") Long id, @Param("tenantId") Long tenantId,
               @Param("acceptRemark") String acceptRemark, @Param("updateBy") Long updateBy);

    /** 采购商验收不合格：received → return_pending，填原因+凭证 */
    int rejectDelivery(@Param("id") Long id, @Param("tenantId") Long tenantId,
                       @Param("returnReason") String returnReason,
                       @Param("returnAttachmentKey") String returnAttachmentKey,
                       @Param("updateBy") Long updateBy);

    /** 采购商部分残次：received → accepted(partial)，记录残次数量与原因+凭证 */
    int partialReject(@Param("id") Long id, @Param("tenantId") Long tenantId,
                      @Param("defectiveQty") Integer defectiveQty,
                      @Param("returnReason") String returnReason,
                      @Param("returnAttachmentKey") String returnAttachmentKey,
                      @Param("updateBy") Long updateBy);

    /** 供应商确认退回：return_pending → return_confirmed */
    int confirmReturn(@Param("id") Long id, @Param("tenantId") Long tenantId,
                      @Param("updateBy") Long updateBy);

    /** 供应商异议：return_pending → received，清空退回痕迹 */
    int disputeReturn(@Param("id") Long id, @Param("tenantId") Long tenantId,
                      @Param("updateBy") Long updateBy);

    /** 采购商填写退货物流：return_confirmed → return_shipping */
    int fillReturnLogistics(@Param("id") Long id, @Param("tenantId") Long tenantId,
                            @Param("returnShipCompany") String returnShipCompany,
                            @Param("returnShipTrackingNo") String returnShipTrackingNo,
                            @Param("updateBy") Long updateBy);

    /** 供应商确认收到退货：return_shipping → returned */
    int confirmReturnReceipt(@Param("id") Long id, @Param("tenantId") Long tenantId,
                             @Param("updateBy") Long updateBy);

    /** 供应商更正运单：仅 shipped，不改发货时间 */
    int correctTracking(@Param("id") Long id,
                        @Param("tenantId") Long tenantId,
                        @Param("shipCompany") String shipCompany,
                        @Param("shipTrackingNo") String shipTrackingNo,
                        @Param("updateBy") Long updateBy);

    /** 按合同统计交付单数（幂等判断） */
    int countByContract(@Param("contractId") Long contractId);

    /** 按合同统计进行中（非 accepted）交付单数（终止校验） */
    int countActiveByContract(@Param("contractId") Long contractId);

    /** 按合同统计交付单总数 */
    long countTotalByContract(@Param("contractId") Long contractId);

    /** 按合同统计已验收交付单数 */
    long countAcceptedByContract(@Param("contractId") Long contractId);

    /** 按合同ID批量查交付单（发货排期 Java 组装用；含补发批 reship_from 非空行） */
    List<Delivery> selectByContractIds(@Param("contractIds") List<Long> contractIds);

    /** 按合同查已有交付单的批次号集合（未发货分批计划判定用，替代跨域直查 contract_batch_plan） */
    List<Integer> selectBatchNosByContract(@Param("contractId") Long contractId);

    /** 按合同取当前最大批次号，无记录返回 0（拆批时据此生成下一批） */
    int selectMaxBatchNo(@Param("contractId") Long contractId);

    /** 按合同汇总累计已发数量，排除指定批次（发货数量总量校验用） */
    int sumShippedQtyByContract(@Param("contractId") Long contractId,
                                @Param("excludeId") Long excludeId);

    /** 按合同汇总已建各批次的应发数量（手动建单算剩余可分配量） */
    int sumQuantityByContract(@Param("contractId") Long contractId);

    /** 按合同汇总已建各批次金额（收尾批用减法兜底） */
    BigDecimal sumAmountByContract(@Param("contractId") Long contractId);

    /** 按合同ID批量查本域分配量聚合（可建交付单合同判定用；合同信息由 trade 的 listSignedBySupplier Feign 提供） */
    List<ContractAllocationRow> selectAllocationStatsByContractIds(@Param("contractIds") List<Long> contractIds);

    /** 取拆分源：待发货批次中数量最大的一个 */
    Delivery selectLargestPendingByContract(@Param("contractId") Long contractId);

    /** 拆批时收窄本批数量与金额 */
    int shrinkBatch(@Param("id") Long id,
                    @Param("quantity") Integer quantity,
                    @Param("amount") BigDecimal amount,
                    @Param("updateBy") Long updateBy);

    /** 逻辑删除批次 */
    int deleteById(@Param("id") Long id, @Param("updateBy") Long updateBy);

    /** 插入履约时间线 */
    int insertTrace(DeliveryTrace trace);

    /** 查交付单时间线（时间正序） */
    List<DeliveryTrace> selectTraceByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** 按合同+批次号查是否已有发货单（防重） */
    int countByContractAndBatchNo(@Param("contractId") Long contractId,
                                  @Param("batchNo") Integer batchNo);

    /** 供应商履约统计：已验收普通批（排除补发批）的样本数/合格数/退回数/准时数 */
    PerformanceStatRow selectPerformanceStat(@Param("tenantId") Long tenantId);

    /** 交期延误预警候选：未闭环且交期临期/超期且未预警过的交付单 */
    List<Delivery> selectDelayWarningCandidates(@Param("deadline") LocalDate deadline);

    /** 确认承诺交期写回（采购方确认后覆盖考核基准） */
    int updateConfirmedDeliveryDate(@Param("id") Long id,
                                    @Param("confirmedDeliveryDate") LocalDate confirmedDeliveryDate,
                                    @Param("updateBy") Long updateBy);

    /** 违约金计算落库（验收时快照） */
    int updatePenalty(@Param("id") Long id,
                      @Param("penaltyDays") Integer penaltyDays,
                      @Param("penaltyAmount") BigDecimal penaltyAmount);
}
