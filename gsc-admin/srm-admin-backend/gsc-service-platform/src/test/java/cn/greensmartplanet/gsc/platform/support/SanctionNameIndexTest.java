package cn.greensmartplanet.gsc.platform.support;

import cn.greensmartplanet.gsc.platform.mapper.SanctionEntryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归：索引是每实例一份的内存 Map，同步只在一个实例执行，
 * 其余实例必须靠 fanout 广播重建，否则筛查结果不一致。
 */
@ExtendWith(MockitoExtension.class)
class SanctionNameIndexTest {

    @Mock
    private SanctionEntryMapper sanctionEntryMapper;

    @InjectMocks
    private SanctionNameIndex index;

    @Test
    @DisplayName("收到刷新广播 → 重新全表加载重建索引")
    void onRefreshBroadcast_rebuilds() {
        when(sanctionEntryMapper.selectList(null)).thenReturn(List.of());

        index.onRefreshBroadcast("rebuild");

        verify(sanctionEntryMapper).selectList(null);
    }

    @Test
    @DisplayName("重建失败不抛异常（旁路刷新，下轮同步/重启会再建）")
    void onRefreshBroadcast_swallowsException() {
        when(sanctionEntryMapper.selectList(null)).thenThrow(new RuntimeException("db down"));

        index.onRefreshBroadcast("rebuild"); // 不抛即通过
    }
}
