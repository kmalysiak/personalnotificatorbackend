package pl.kmalysiak.notificator.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wiersz tabeli słownikowej. Jeden "słownik" (identyfikowany przez {@code dictNum}) to zbiór
 * wielu wierszy - {@code dictNum}/{@code dictDesc} będą się więc wielokrotnie powtarzać
 * (denormalizacja celowa, dla prostoty odczytu/administracji przez jedną tabelę).
 */
@Entity
@Table(
        name = "dictionary",
        schema = "public",
        indexes = {
                @Index(
                        name = "idx_dictionary_num_code",
                        columnList = "dict_num, code"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DictionaryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** numer słownika, np. 1 = operational_status pompy Haier */
    private Integer dictNum;

    /** opis słownika, np. "Haier - operational status" - powtarzalny dla wszystkich wierszy danego dictNum */
    private String dictDesc;

    /** surowy kod, np. "16" */
    private String code;

    /** czytelna wartość odpowiadająca kodowi, np. "grzanie CWU" */
    private String value;
}
