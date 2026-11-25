package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_file_data")
@Setter
@Getter
@NoArgsConstructor
/* Klasa konternera przechowujaca informacje o plikach */
public class FileData extends GeneratedIdEntity {
    /* nazwa pliku */
    private String fileName;
    /* typ pliku */
    private String fileType;
    /* wywolanie pliku */
    private String localUrl;
    /* sciezka */
    private String path;
    /* rozmiar */
    private Long size;
}
