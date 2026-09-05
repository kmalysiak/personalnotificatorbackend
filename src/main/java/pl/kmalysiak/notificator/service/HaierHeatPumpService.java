package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.dto.DictValue;
import pl.kmalysiak.notificator.dto.HaierHeatPumpDto;
import pl.kmalysiak.notificator.model.entity.HaEntity;
import pl.kmalysiak.notificator.repo.HaEntityRepo;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the monitored Haier heat pump (garaz_haier_sniffer) entities from {@code ha_entity}
 * and assembles them into a single {@link HaierHeatPumpDto} snapshot.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HaierHeatPumpService {

    private static final String ENTITY_OPERATIONAL_STATUS = "sensor.garaz_haier_sniffer_operational_status";
    private static final String ENTITY_HEAT_PUMP_MODE = "sensor.garaz_haier_sniffer_heat_pump_mode";
    private static final String ENTITY_ENERGY_MODE = "sensor.garaz_haier_sniffer_energy_mode";
    private static final String ENTITY_CURRENT_ERROR_CODE = "sensor.garaz_haier_sniffer_current_error_code";
    private static final String ENTITY_ERROR_CODE_ALT = "sensor.garaz_haier_sniffer_r141_lsb_error_code_alt";
    private static final String ENTITY_DEFROST_ACTIVE = "binary_sensor.garaz_haier_sniffer_defrost_active";
    private static final String ENTITY_ANTIFREEZE_PROTECTION_ACTIVE = "binary_sensor.garaz_haier_sniffer_antifreeze_protection_active";
    private static final String ENTITY_THREE_WAY_VALVE_TO_CH = "binary_sensor.garaz_haier_sniffer_3_way_valve_to_ch";
    private static final String ENTITY_THREE_WAY_VALVE_TO_DHW = "binary_sensor.garaz_haier_sniffer_3_way_valve_to_dhw";
    private static final String ENTITY_CIRCULATION_PUMP = "binary_sensor.garaz_haier_sniffer_circulation_pump";
    private static final String ENTITY_BACKUP_RESISTIVE_HEATER = "binary_sensor.garaz_haier_sniffer_backup_resistive_heater";
    private static final String ENTITY_DHW_BUFFER_TEMPERATURE = "sensor.garaz_haier_sniffer_dhw_buffer_temperature";
    private static final String ENTITY_COMPRESSOR_FREQUENCY_ACTUAL = "sensor.garaz_haier_sniffer_compressor_frequency_actual";
    private static final String ENTITY_FAN_1_RPM = "sensor.garaz_haier_sniffer_fan_1_rpm";
    private static final String ENTITY_DHW_ENERGY_CONSUMPTION = "sensor.garaz_haier_sniffer_dhw_energy_consumption";
    // nie ma jeszcze w ha_entity - integracja jeszcze tego nie publikuje, zarezerwowane na przyszłość
    private static final String ENTITY_CH_ENERGY_CONSUMPTION = "sensor.garaz_haier_sniffer_ch_energy_consumption";
    private static final String ENTITY_WATER_INLET_TEMPERATURE_TWI = "sensor.garaz_haier_sniffer_water_inlet_temperature_twi";
    private static final String ENTITY_WATER_OUTLET_TEMPERATURE_TWO = "sensor.garaz_haier_sniffer_water_outlet_temperature_two";
    private static final String ENTITY_COMPRESSOR_INTERNAL_TEMPERATURE = "sensor.garaz_haier_sniffer_compressor_internal_temperature";
    private static final String ENTITY_DISCHARGE_GAS_TEMPERATURE_TD = "sensor.garaz_haier_sniffer_discharge_gas_temperature_td";
    private static final String ENTITY_SUCTION_PRESSURE_ACTUAL_PSA = "sensor.garaz_haier_sniffer_suction_pressure_actual_psa";
    private static final String ENTITY_DISCHARGE_PRESSURE_ACTUAL_PDACT = "sensor.garaz_haier_sniffer_discharge_pressure_actual_pdact";
    private static final String ENTITY_AMBIENT_TEMPERATURE_TAO = "sensor.garaz_haier_sniffer_ambient_temperature_tao";
    private static final String ENTITY_COP_ESTIMATED = "sensor.garaz_haier_sniffer_cop_estimated";
    private static final String ENTITY_SUCTION_GAS_TEMPERATURE_TS = "sensor.garaz_haier_sniffer_suction_gas_temperature_ts";
    private static final String ENTITY_UNKNOWN_QUERY_COUNT = "sensor.garaz_haier_sniffer_unknown_query_count";

    private static final List<String> MONITORED_ENTITY_IDS = List.of(
            ENTITY_OPERATIONAL_STATUS,
            ENTITY_HEAT_PUMP_MODE,
            ENTITY_ENERGY_MODE,
            ENTITY_CURRENT_ERROR_CODE,
            ENTITY_ERROR_CODE_ALT,
            ENTITY_DEFROST_ACTIVE,
            ENTITY_ANTIFREEZE_PROTECTION_ACTIVE,
            ENTITY_THREE_WAY_VALVE_TO_CH,
            ENTITY_THREE_WAY_VALVE_TO_DHW,
            ENTITY_CIRCULATION_PUMP,
            ENTITY_BACKUP_RESISTIVE_HEATER,
            ENTITY_DHW_BUFFER_TEMPERATURE,
            ENTITY_COMPRESSOR_FREQUENCY_ACTUAL,
            ENTITY_FAN_1_RPM,
            ENTITY_DHW_ENERGY_CONSUMPTION,
            ENTITY_CH_ENERGY_CONSUMPTION,
            ENTITY_WATER_INLET_TEMPERATURE_TWI,
            ENTITY_WATER_OUTLET_TEMPERATURE_TWO,
            ENTITY_COMPRESSOR_INTERNAL_TEMPERATURE,
            ENTITY_DISCHARGE_GAS_TEMPERATURE_TD,
            ENTITY_SUCTION_PRESSURE_ACTUAL_PSA,
            ENTITY_DISCHARGE_PRESSURE_ACTUAL_PDACT,
            ENTITY_AMBIENT_TEMPERATURE_TAO,
            ENTITY_COP_ESTIMATED,
            ENTITY_SUCTION_GAS_TEMPERATURE_TS,
            ENTITY_UNKNOWN_QUERY_COUNT
    );

    private final HaEntityRepo repo;
    private final DictionaryService dictionaryService;

    public HaierHeatPumpDto getHaierData() {
        Map<String, HaEntity> byEntityId = repo.findByEntityIdIn(MONITORED_ENTITY_IDS).stream()
                .collect(Collectors.toMap(HaEntity::getEntityId, Function.identity()));

        HaierHeatPumpDto dto = new HaierHeatPumpDto();
        dto.setTimestamp(TimeZoneUtils.getLocalDateTimeNow());

        dto.setOperationalStatus(toDictValue(byEntityId.get(ENTITY_OPERATIONAL_STATUS)));
        dto.setHeatPumpMode(toDictValue(byEntityId.get(ENTITY_HEAT_PUMP_MODE)));
        dto.setEnergyMode(toDictValue(byEntityId.get(ENTITY_ENERGY_MODE)));
        dto.setCurrentErrorCode(toDictValue(byEntityId.get(ENTITY_CURRENT_ERROR_CODE)));
        dto.setR141LsbErrorCodeAlt(toDictValue(byEntityId.get(ENTITY_ERROR_CODE_ALT)));

        dto.setDefrostActive(toBoolean(byEntityId.get(ENTITY_DEFROST_ACTIVE)));
        dto.setAntifreezeProtectionActive(toBoolean(byEntityId.get(ENTITY_ANTIFREEZE_PROTECTION_ACTIVE)));
        dto.setThreeWayValveToCh(toBoolean(byEntityId.get(ENTITY_THREE_WAY_VALVE_TO_CH)));
        dto.setThreeWayValveToDhw(toBoolean(byEntityId.get(ENTITY_THREE_WAY_VALVE_TO_DHW)));
        dto.setCirculationPump(toBoolean(byEntityId.get(ENTITY_CIRCULATION_PUMP)));
        dto.setBackupResistiveHeater(toBoolean(byEntityId.get(ENTITY_BACKUP_RESISTIVE_HEATER)));

        dto.setDhwBufferTemperature(toBigDecimal(byEntityId.get(ENTITY_DHW_BUFFER_TEMPERATURE)));
        dto.setCompressorFrequencyActual(toBigDecimal(byEntityId.get(ENTITY_COMPRESSOR_FREQUENCY_ACTUAL)));
        dto.setFan1Rpm(toBigDecimal(byEntityId.get(ENTITY_FAN_1_RPM)));
        dto.setDhwEnergyConsumption(toBigDecimal(byEntityId.get(ENTITY_DHW_ENERGY_CONSUMPTION)));
        dto.setChEnergyConsumption(toBigDecimal(byEntityId.get(ENTITY_CH_ENERGY_CONSUMPTION)));
        dto.setWaterInletTemperatureTwi(toBigDecimal(byEntityId.get(ENTITY_WATER_INLET_TEMPERATURE_TWI)));
        dto.setWaterOutletTemperatureTwo(toBigDecimal(byEntityId.get(ENTITY_WATER_OUTLET_TEMPERATURE_TWO)));
        dto.setCompressorInternalTemperature(toBigDecimal(byEntityId.get(ENTITY_COMPRESSOR_INTERNAL_TEMPERATURE)));
        dto.setDischargeGasTemperatureTd(toBigDecimal(byEntityId.get(ENTITY_DISCHARGE_GAS_TEMPERATURE_TD)));
        dto.setSuctionPressureActualPsa(toBigDecimal(byEntityId.get(ENTITY_SUCTION_PRESSURE_ACTUAL_PSA)));
        dto.setDischargePressureActualPdact(toBigDecimal(byEntityId.get(ENTITY_DISCHARGE_PRESSURE_ACTUAL_PDACT)));
        dto.setAmbientTemperatureTao(toBigDecimal(byEntityId.get(ENTITY_AMBIENT_TEMPERATURE_TAO)));
        dto.setCopEstimated(toBigDecimal(byEntityId.get(ENTITY_COP_ESTIMATED)));
        dto.setSuctionGasTemperatureTs(toBigDecimal(byEntityId.get(ENTITY_SUCTION_GAS_TEMPERATURE_TS)));
        dto.setUnknownQueryCount(toBigDecimal(byEntityId.get(ENTITY_UNKNOWN_QUERY_COUNT)));

        dictionaryService.resolveDictionaryFields(dto);

        return dto;
    }

    private static Boolean toBoolean(HaEntity entity) {
        if (entity == null || entity.getCurrState() == null) return null;
        return switch (entity.getCurrState().trim().toLowerCase()) {
            case "on" -> true;
            case "off" -> false;
            default -> null;
        };
    }

    /**
     * Buduje DictValue z surowym kodem (bez przetłumaczonej value - tym zajmie się
     * {@link DictionaryService#resolveDictionaryFields(Object)} wywołane na końcu {@link #getHaierData()}).
     * curr_state dla statusów przychodzi z HA jako np. "16.0" - normalizujemy do "16", żeby
     * pasowało do kodu zapisanego w tabeli dictionary.
     */
    private static DictValue toDictValue(HaEntity entity) {
        String code = toStatusCode(entity);
        return code == null ? null : new DictValue(code, null);
    }

    private static String toStatusCode(HaEntity entity) {
        BigDecimal value = toBigDecimal(entity);
        if (value == null) return null;
        if (value.signum() == 0) return "0";

        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
    }

    private static BigDecimal toBigDecimal(HaEntity entity) {
        if (entity == null || entity.getCurrState() == null || entity.getCurrState().isBlank()) return null;
        try {
            return new BigDecimal(entity.getCurrState().trim());
        } catch (NumberFormatException e) {
            log.warn("Nie udało się sparsować wartości liczbowej '{}' dla encji:{}", entity.getCurrState(), entity.getEntityId());
            return null;
        }
    }
}
