package cn.greensmartplanet.gsc.platform.vo;

import cn.greensmartplanet.gsc.common.util.DdPackageCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 尽调套餐价目表 — 平台开通币种独立定价，不做汇率折算。
 */
@Data
@Schema(description = "尽调套餐价目表")
public class DdPackageCatalogVO {

    @Schema(description = "已开通支付币种，顺序即前端下拉顺序")
    private List<String> currencies;

    @Schema(description = "套餐 → 币种 → 最小货币单位金额")
    private Map<String, Map<String, Long>> prices;

    public static DdPackageCatalogVO fromCatalog() {
        DdPackageCatalogVO vo = new DdPackageCatalogVO();
        vo.setCurrencies(DdPackageCatalog.CURRENCIES);
        vo.setPrices(DdPackageCatalog.prices());
        return vo;
    }
}
