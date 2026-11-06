package pl.kmalysiak.notificator.dto;

import lombok.Data;
import pl.kmalysiak.notificator.annotation.Dictionary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of the monitored Haier heat pump (external unit sniffer) properties,
 * assembled from {@code ha_entity} rows (entity_id prefix {@code garaz_haier_sniffer_}).
 * <p>
 * Field grouping:
 * <ul>
 *     <li>status codes ({@link DictValue}, code translated to a human value via the słownik
 *     identified by the field's {@code @Dictionary} number)</li>
 *     <li>binary on/off states ({@code Boolean}, parsed from curr_state "on"/"off")</li>
 *     <li>numeric measurements (raw {@code BigDecimal}, units noted per field)</li>
 * </ul>
 */
@Data
public class HaierHeatPumpDto {

    /** moment this snapshot was assembled by {@code HaierHeatPumpService#getHaierData()} */
    private LocalDateTime timestamp;

    /** ogólny komentarz do snapshotu - na razie zawsze {@code null}, zarezerwowane na przyszłość */
    private String comment;

    /** błędy per-pole - na razie zawsze pusta lista, zarezerwowane na przyszłość */
    private List<FieldError> errors = new ArrayList<>();

    // ---- status codes (DictValue, translated via słownik) ----

    /** sensor.garaz_haier_sniffer_operational_status - słownik nr 1 */
    @Dictionary(1)
    private DictValue operationalStatus;

    /** sensor.garaz_haier_sniffer_heat_pump_mode - słownik nr 2 */
    @Dictionary(2)
    private DictValue heatPumpMode;

    /** sensor.garaz_haier_sniffer_energy_mode - słownik nr 3 */
    @Dictionary(3)
    private DictValue energyMode;

    /** sensor.garaz_haier_sniffer_current_error_code - słownik nr 4 */
    @Dictionary(4)
    private DictValue currentErrorCode;

    /**
     * sensor.garaz_haier_sniffer_r141_lsb_error_code_alt - alternatywny/zapasowy kod błędu
     * z innego rejestru (R141 LSB) - własny, dedykowany słownik nr 5 (inny niż currentErrorCode).
     */
    @Dictionary(5)
    private DictValue r141LsbErrorCodeAlt;

    // ---- statusy zdekodowane na tekst po stronie ESP (sniffer) ----

    /**
     * sensor.garaz_haier_sniffer_heat_pump_mode_decoded - tekst z rejestru R141 dekodowany przez ESP,
     * np. "c.o.", "CWU", "odszranianie (CWU)", z dopiskiem "+ antyzamarzanie", albo "nieznany (N)".
     * Niezależne od słownika nr 2 ({@link #heatPumpMode}) - do porównania.
     */
    private String heatPumpModeDecoded;

    /**
     * sensor.garaz_haier_sniffer_operational_status_decoded - tekst z rejestru R144 dekodowany przez ESP,
     * np. "postój", "rozruch", "wygaszanie", "praca", z dopiskiem "+ grzałka", albo "nieznany (N)".
     * Niezależne od słownika nr 1 ({@link #operationalStatus}) - do porównania.
     */
    private String operationalStatusDecoded;

    // ---- binary on/off states (curr_state "on"/"off" parsed to boolean) ----

    /** binary_sensor.garaz_haier_sniffer_defrost_active */
    private Boolean defrostActive;

    /** binary_sensor.garaz_haier_sniffer_antifreeze_protection_active */
    private Boolean antifreezeProtectionActive;

    /** binary_sensor.garaz_haier_sniffer_3_way_valve_to_ch */
    private Boolean threeWayValveToCh;

    /** binary_sensor.garaz_haier_sniffer_3_way_valve_to_dhw */
    private Boolean threeWayValveToDhw;

    /** binary_sensor.garaz_haier_sniffer_circulation_pump */
    private Boolean circulationPump;

    /** binary_sensor.garaz_haier_sniffer_backup_resistive_heater */
    private Boolean backupResistiveHeater;

    /**
     * binary_sensor.garaz_haier_sniffer_defrost_r156_bit_4 - druga, niezależna flaga odszraniania
     * (bit 4 rejestru R156), do porównania z {@link #defrostActive} (bit 5 rejestru R141).
     */
    private Boolean defrostR156Bit4;

    // ---- numeric measurements ----

    /** sensor.garaz_haier_sniffer_dhw_buffer_temperature [°C] */
    private BigDecimal dhwBufferTemperature;

    /** sensor.garaz_haier_sniffer_compressor_frequency_actual [Hz] */
    private BigDecimal compressorFrequencyActual;

    /** sensor.garaz_haier_sniffer_fan_1_rpm [rpm] */
    private BigDecimal fan1Rpm;

    /** sensor.garaz_haier_sniffer_dhw_energy_consumption [kWh] */
    private BigDecimal dhwEnergyConsumption;

    /**
     * sensor.garaz_haier_sniffer_ch_energy_consumption [kWh]
     * Not present in ha_entity yet — reserved for when the sniffer integration
     * exposes central-heating (CO) energy consumption.
     */
    private BigDecimal chEnergyConsumption;

    /** sensor.garaz_haier_sniffer_water_inlet_temperature_twi [°C] */
    private BigDecimal waterInletTemperatureTwi;

    /** sensor.garaz_haier_sniffer_water_outlet_temperature_two [°C] */
    private BigDecimal waterOutletTemperatureTwo;

    /** sensor.garaz_haier_sniffer_compressor_internal_temperature [°C] */
    private BigDecimal compressorInternalTemperature;

    /** sensor.garaz_haier_sniffer_discharge_gas_temperature_td [°C] */
    private BigDecimal dischargeGasTemperatureTd;

    /** sensor.garaz_haier_sniffer_suction_pressure_actual_psa [bar] */
    private BigDecimal suctionPressureActualPsa;

    /** sensor.garaz_haier_sniffer_discharge_pressure_actual_pdact [bar] */
    private BigDecimal dischargePressureActualPdact;

    /** sensor.garaz_haier_sniffer_ambient_temperature_tao [°C] */
    private BigDecimal ambientTemperatureTao;

    /** sensor.garaz_haier_sniffer_cop_estimated [COP] */
    private BigDecimal copEstimated;

    /** sensor.garaz_haier_sniffer_suction_gas_temperature_ts [°C] */
    private BigDecimal suctionGasTemperatureTs;

    /** sensor.garaz_haier_sniffer_unknown_query_count - licznik debugowy protokołu sniffera */
    private BigDecimal unknownQueryCount;

    /**
     * number.garaz_haier_sniffer_ch_water_flow_assumed [L/min] - założony przepływ w obiegu c.o.
     * (konfiguracja w HA, zakres 5-30, krok 0,5, domyślnie 13); wchodzi do wzoru na {@link #copEstimated}.
     */
    private BigDecimal chWaterFlowAssumed;

    // ---- diagnostyka ESP (sniffera) ----

    /** sensor.garaz_haier_sniffer_wifi_signal [dBm] - siła sygnału Wi-Fi ESP, odświeżane co 60 s */
    private BigDecimal wifiSignal;

    /**
     * sensor.garaz_haier_sniffer_uptime [s] - czas od ostatniego startu ESP, odświeżane co 60 s.
     * Nagły spadek = restart ESP (przy samej utracie Wi-Fi rośnie dalej).
     */
    private BigDecimal uptime;
}
