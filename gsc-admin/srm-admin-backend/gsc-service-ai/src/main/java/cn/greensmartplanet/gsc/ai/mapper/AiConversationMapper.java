package cn.greensmartplanet.gsc.ai.mapper;

import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 会话 Mapper
 * <p>
 * 多租户：列表/详情手写 SQL 过滤 tenant_id + is_deleted = 0。
 *
 * @author gsc
 */
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    /**
     * 按租户 + 用户查会话列表（update_time 倒序，仅 active）
     */
    List<AiConversation> selectByTenantUser(@Param("tenantId") Long tenantId,
                                            @Param("userId") Long userId);

    /**
     * 校验会话归属（租户 + 用户隔离：防止越权操作他人会话）
     */
    AiConversation selectByIdAndTenantUser(@Param("id") Long id,
                                           @Param("tenantId") Long tenantId,
                                           @Param("userId") Long userId);

    /**
     * CAS 推进摘要与覆盖点：仅当覆盖点仍等于读到的旧值时才更新。
     * <p>
     * 并发压缩（连续两轮生成各自触发 / 多实例）时后到者读到的是同一个旧水位线，
     * 不做 CAS 会各自基于旧摘要压缩并互相覆盖，水位线甚至可能回退。
     *
     * @param id           会话ID
     * @param summary      新摘要
     * @param upToMsgId    新覆盖点（本批最后一条的 id）
     * @param oldUpToMsgId 本次读到的旧覆盖点，可为 null（首次压缩）
     * @return 影响行数；0 表示已被别的压缩推进过，本次放弃写回
     */
    int updateSummaryIfUnchanged(@Param("id") Long id,
                                 @Param("summary") String summary,
                                 @Param("upToMsgId") Long upToMsgId,
                                 @Param("oldUpToMsgId") Long oldUpToMsgId);
}
