package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 我的对接状态契约 DTO — 供应商域内部接口返回给交易域需求广场的对接状态子集
 * <p>
 * 交易域需求广场回填"当前租户对某需求"的对接状态与拒绝原因。
 * 契约约定：无对接记录时该需求不在 map 中。
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyConnectionStatusDTO {

    /** 对接状态：pending/accepted/rejected/closed */
    private String status;
    /** 拒绝原因（rejected 时有值） */
    private String replyMessage;
}
