package cn.greensmartplanet.gsc.framework.weather;

import java.util.Set;

/**
 * Open-Meteo 命中是否像「城市」而不是同名乡镇。
 * <p>
 * GeoNames feature：PPLC/PPLA/PPLA2/PPLA3 是行政驻地；
 * 人口过低的 PPL/PPLA4（如重庆西湖镇、福建天河村）不能当天气落点。
 */
final class GeoReliability {

    private static final Set<String> CITY_FEATURES = Set.of("PPLC", "PPLA", "PPLA2", "PPLA3");
    private static final int MIN_POPULATION = 100_000;

    private GeoReliability() {
    }

    static boolean isCityLevel(GeoLocation location) {
        if (location == null) {
            return false;
        }
        if (PlaceNameResolver.isAmbiguousAtom(location.getName())) {
            return false;
        }
        String feature = location.getFeatureCode();
        if (feature != null && CITY_FEATURES.contains(feature)) {
            return true;
        }
        Integer population = location.getPopulation();
        return population != null && population >= MIN_POPULATION;
    }
}
