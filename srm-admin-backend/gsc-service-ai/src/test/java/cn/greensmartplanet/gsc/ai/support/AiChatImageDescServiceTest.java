package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.dto.ImageDescriptionDTO;
import cn.greensmartplanet.gsc.ai.entity.AiChatImage;
import cn.greensmartplanet.gsc.ai.entity.AiMessageImage;
import cn.greensmartplanet.gsc.ai.mapper.AiChatImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图片描述服务测试。
 * <p>
 * 要锁住的核心语义：{@code description} 三态——null=未生成（补）、空串=生成过但无内容
 * （不重试）、非空=直接用。留 null 会让坏图每轮重试白烧 token。
 */
@ExtendWith(MockitoExtension.class)
class AiChatImageDescServiceTest {

    @Mock
    private AiChatImageMapper chatImageMapper;
    @Mock
    private AiMessageImageMapper messageImageMapper;
    @Mock
    private OssService ossService;
    @Mock
    private ObjectMapper objectMapper;

    /**
     * LambdaUpdateWrapper 需要实体的 TableInfo 缓存（正常由 MP 启动时建），单测无容器必须手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiChatImage.class);
    }

    private AiChatImageDescService service() {
        return spy(new AiChatImageDescService(
                chatImageMapper, messageImageMapper, ossService, objectMapper));
    }

    private AiMessageImage rel(Long messageId, Long imageId) {
        return AiMessageImage.builder().messageId(messageId).imageId(imageId).build();
    }

    private AiChatImage img(Long id, String desc) {
        return AiChatImage.builder().id(id).tenantId(1L).ossKey("ai-chat/" + id + ".jpg")
                .description(desc).build();
    }

    // ==================== 批量读取 ====================

    @Test
    @DisplayName("一条消息挂多张图时按上传序返回列表，序号 1 起")
    void loadImageContext_returnsOrderedList() {
        when(messageImageMapper.selectByMessageIds(List.of(10L), 1L))
                .thenReturn(List.of(rel(10L, 100L), rel(10L, 101L)));
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L, 101L), 1L))
                .thenReturn(List.of(img(100L, "合同扫描件，编号 HT001"), img(101L, "发票，金额 320 万")));

        AiChatImageDescService.ImageContext ctx = service().loadImageContext(List.of(10L), 1L);
        List<ImageDescriptionDTO> descs = ctx.descriptionsByMessage().get(10L);

        assertThat(descs).hasSize(2);
        assertThat(descs.get(0).getIndex()).isEqualTo(1);
        assertThat(descs.get(0).getDescription()).isEqualTo("合同扫描件，编号 HT001");
        assertThat(descs.get(1).getIndex()).isEqualTo(2);
        assertThat(descs.get(1).getDescription()).isEqualTo("发票，金额 320 万");
        assertThat(ctx.messageIdsWithImages()).containsExactly(10L);
    }

    @Test
    @DisplayName("未生成描述也要带条目（空描述），否则序号断档、模型会把第 N 张答成别张")
    void loadImageContext_keepsEntryWithBlankDescription() {
        when(messageImageMapper.selectByMessageIds(List.of(10L), 1L)).thenReturn(List.of(rel(10L, 100L)));
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L), 1L))
                .thenReturn(List.of(img(100L, null)));

        AiChatImageDescService.ImageContext ctx = service().loadImageContext(List.of(10L), 1L);

        assertThat(ctx.messageIdsWithImages()).containsExactly(10L);
        List<ImageDescriptionDTO> descs = ctx.descriptionsByMessage().get(10L);
        assertThat(descs).hasSize(1);
        assertThat(descs.get(0).getIndex()).isEqualTo(1);
        assertThat(descs.get(0).getDescription()).isEmpty();
    }

    @Test
    @DisplayName("中间某张无描述也保留序号（「第 2 张」仍指第 2 张）")
    void loadImageContext_keepsIndexWhenDescriptionBlank() {
        when(messageImageMapper.selectByMessageIds(List.of(10L), 1L))
                .thenReturn(List.of(rel(10L, 100L), rel(10L, 101L), rel(10L, 102L)));
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L, 101L, 102L), 1L))
                .thenReturn(List.of(img(100L, ""), img(101L, "第二张的描述"), img(102L, "第三张的描述")));

        List<ImageDescriptionDTO> descs = service().loadImageContext(List.of(10L), 1L)
                .descriptionsByMessage().get(10L);

        assertThat(descs).hasSize(3);
        assertThat(descs.get(0).getIndex()).isEqualTo(1);
        assertThat(descs.get(0).getDescription()).isEmpty();
        assertThat(descs.get(1).getIndex()).isEqualTo(2);
        assertThat(descs.get(1).getDescription()).isEqualTo("第二张的描述");
        assertThat(descs.get(2).getIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("无图片消息不进结果，空入参不查库")
    void loadImageContext_handlesEmpty() {
        assertThat(service().loadImageContext(List.of(), 1L).messageIdsWithImages()).isEmpty();
        assertThat(service().loadImageContext(null, 1L).descriptionsByMessage()).isEmpty();
        verify(messageImageMapper, never()).selectByMessageIds(any(), any());
    }

    // ==================== 上传时触发（主路径） ====================

    @Test
    @DisplayName("上传即生成：入参是图片ID，不需要消息关联")
    void ensureAsyncForImages_generatesDirectly() throws Exception {
        AiChatImageDescService svc = service();
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L), 1L))
                .thenReturn(List.of(img(100L, null)));
        doReturn("新描述").when(svc).callDescribe(any());

        svc.ensureAsyncForImages(List.of(100L), 1L);

        verify(svc).callDescribe(any());
        verify(messageImageMapper, never()).selectByMessageIds(any(), any());
    }

    @Test
    @DisplayName("上传路径下已有描述不重复生成")
    void ensureAsyncForImages_skipsAlreadyDescribed() throws Exception {
        AiChatImageDescService svc = service();
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L), 1L))
                .thenReturn(List.of(img(100L, "已有描述")));

        svc.ensureAsyncForImages(List.of(100L), 1L);

        verify(svc, never()).callDescribe(any());
    }

    // ==================== 按历史消息兜底 ====================

    @Test
    @DisplayName("只补 description 为 null 的，空串不重试")
    void ensureAsyncByMessages_onlyGeneratesMissing() throws Exception {
        AiChatImageDescService svc = service();
        when(messageImageMapper.selectByMessageIds(List.of(10L), 1L))
                .thenReturn(List.of(rel(10L, 100L), rel(10L, 101L)));
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L, 101L), 1L))
                .thenReturn(List.of(img(100L, null), img(101L, "")));
        doReturn("新描述").when(svc).callDescribe(any());

        svc.ensureAsyncByMessages(List.of(10L), 1L);

        verify(svc, times(1)).callDescribe(any());
        verify(chatImageMapper, times(1)).update(isNull(), any());
    }

    @Test
    @DisplayName("已有描述的图不重复生成")
    void ensureAsyncByMessages_skipsAlreadyDescribed() throws Exception {
        AiChatImageDescService svc = service();
        when(messageImageMapper.selectByMessageIds(List.of(10L), 1L)).thenReturn(List.of(rel(10L, 100L)));
        when(chatImageMapper.selectByIdsAndTenant(List.of(100L), 1L))
                .thenReturn(List.of(img(100L, "已有描述")));

        svc.ensureAsyncByMessages(List.of(10L), 1L);

        verify(svc, never()).callDescribe(any());
    }

    @Test
    @DisplayName("单张失败写空串，不抛异常、不影响其它张")
    void generateOne_failureWritesBlank() throws Exception {
        AiChatImageDescService svc = service();
        doThrow(new RuntimeException("多模态接口超时")).when(svc).callDescribe(any());

        String desc = svc.generateOne(img(100L, null), 1L);

        assertThat(desc).isEmpty();
        verify(chatImageMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("接口非 200（callDescribe 返回 null）同样写空串")
    void generateOne_nullResultWritesBlank() throws Exception {
        AiChatImageDescService svc = service();
        doReturn(null).when(svc).callDescribe(any());

        assertThat(svc.generateOne(img(100L, null), 1L)).isEmpty();
    }

    @Test
    @DisplayName("补生成整体异常不外传（旁路增强，不能影响主对话）")
    void ensureAsyncByMessages_swallowsException() {
        AiChatImageDescService svc = service();
        when(messageImageMapper.selectByMessageIds(any(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> svc.ensureAsyncByMessages(List.of(10L), 1L)).doesNotThrowAnyException();
    }
}
