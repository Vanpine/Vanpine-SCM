package cn.greensmartplanet.gsc.framework.weather;

/**
 * 天气数据客户端 — 项目统一的外部天气数据入口
 * <p>
 * 设计取向（与项目对外部依赖的处理约定一致）：
 * <ul>
 *   <li>失败不抛异常：调用方（定时扫描 / 物流节点录入 / AI 工具）不能因第三方不可用而中断，
 *       失败统一返回 null + warn 日志，由业务侧决定降级表现</li>
 *   <li>结果带 TTL Redis 缓存：多实例共享，定时扫描同一批城市不重复打第三方</li>
 *   <li>只负责取数与归一化：天气业务规则（阈值、风险等级、通知）留在交付域，框架层不掺业务判断</li>
 * </ul>
 * <p>
 * 当前实现：Open-Meteo（全球覆盖、免 key、无官方灾害预警）。
 * 若后续要接官方灾害预警（如和风天气 warning/now、OpenWeather alerts），
 * 新增一个本接口实现并在 GscFrameworkAutoConfiguration 切换装配即可，业务侧零改动。
 *
 * @author gsc
 */
public interface WeatherClient {

    /**
     * 地名 → 经纬度
     * <p>
     * 交付单/合同里的交货地是自由文本，取天气前必须先解析坐标。
     *
     * @param name 地名（可为详细地点，如 杭州西湖；会先抽城市再解析）
     * @return 解析结果，未命中或调用失败返回 null
     */
    GeoLocation geocode(String name);

    /**
     * 实时天气
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @return 实时天气快照，调用失败返回 null
     */
    WeatherNow now(double latitude, double longitude);

    /**
     * 未来天气预报（逐日）
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param days      预报天数（1-16）
     * @return 逐日预报，调用失败返回 null
     */
    WeatherForecast forecast(double latitude, double longitude, int days);
}