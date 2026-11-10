package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.entity.AiDocument;
import cn.greensmartplanet.gsc.ai.mapper.AiDocParentChunkMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiDocumentMapper;
import cn.greensmartplanet.gsc.ai.vo.DocumentVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库文档内容哈希去重测试。
 * <p>
 * 锁定：同租户同内容不重复落 OSS / 不重复建向量；failed 记录复用原行重跑；
 * 并发上传由唯一索引兜底并复用胜出记录。
 */
@ExtendWith(MockitoExtension.class)
class AiDocumentDedupeTest {

    @Mock
    private AiDocumentMapper documentMapper;
    @Mock
    private AiDocParentChunkMapper parentChunkMapper;
    @Mock
    private OssService ossService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient aiRestClient;

    @InjectMocks
    private AiDocumentServiceImpl service;

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "周报.docx", "application/octet-stream",
                "same-content".getBytes());
    }

    @Test
    @DisplayName("已存在 ready 文档：直接复用，不再落 OSS / 不再插入")
    void reuseReadyDocument() throws Exception {
        AiDocument existing = AiDocument.builder()
                .id(9L).tenantId(3L).fileName("周报.docx")
                .status("ready").chunkCount(14).build();
        when(documentMapper.selectByTenantAndHash(eq(3L), anyString())).thenReturn(existing);

        DocumentVO vo = service.upload(file(), 3L, 1L);

        assertThat(vo.getDuplicate()).isTrue();
        assertThat(vo.getId()).isEqualTo(9L);
        verify(ossService, never()).upload(any(), anyString());
        verify(documentMapper, never()).insert(any(AiDocument.class));
    }

    @Test
    @DisplayName("已存在 failed 文档：复用原行重跑解析，不新建记录")
    void reuseFailedDocument() throws Exception {
        AiDocument existing = AiDocument.builder()
                .id(9L).tenantId(3L).fileName("周报.docx").ossKey("ai-doc/old.docx")
                .status("failed").errorMessage("解析失败").build();
        when(documentMapper.selectByTenantAndHash(eq(3L), anyString())).thenReturn(existing);
        when(ossService.generateGetUrl("ai-doc/old.docx")).thenReturn("http://signed");
        when(documentMapper.selectByIdAndTenant(9L, 3L)).thenReturn(AiDocument.builder()
                .id(9L).tenantId(3L).fileName("周报.docx").status("ready").chunkCount(14).build());

        DocumentVO vo = service.upload(file(), 3L, 1L);

        assertThat(vo.getDuplicate()).isTrue();
        verify(ossService, never()).upload(any(), anyString());
        verify(documentMapper, never()).insert(any(AiDocument.class));
        // failed → processing 的复用写回
        verify(documentMapper, org.mockito.Mockito.atLeastOnce()).updateById(any(AiDocument.class));
    }

    @Test
    @DisplayName("并发上传撞唯一索引：删除本次 OSS 对象并复用胜出记录")
    void concurrentUploadReusesWinner() throws Exception {
        AiDocument winner = AiDocument.builder()
                .id(10L).tenantId(3L).fileName("周报.docx")
                .status("ready").chunkCount(14).build();
        // 第一次查不到，插入撞唯一索引后再查得到
        when(documentMapper.selectByTenantAndHash(eq(3L), anyString()))
                .thenReturn(null, winner);
        when(ossService.upload(any(), anyString())).thenReturn("ai-doc/new.docx");
        doThrow(new DuplicateKeyException("uk_tenant_active_hash"))
                .when(documentMapper).insert(any(AiDocument.class));

        DocumentVO vo = service.upload(file(), 3L, 1L);

        assertThat(vo.getDuplicate()).isTrue();
        assertThat(vo.getId()).isEqualTo(10L);
        verify(ossService).deleteObject("ai-doc/new.docx");
    }
}
