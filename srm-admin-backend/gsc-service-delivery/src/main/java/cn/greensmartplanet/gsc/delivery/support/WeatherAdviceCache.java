package cn.greensmartplanet.gsc.delivery.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气 AI 建议缓存（Redis JSON）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherAdviceCache {

    /** generating / ready / fallback */
    private String status;
    private List<String> lines = new ArrayList<>();
}
