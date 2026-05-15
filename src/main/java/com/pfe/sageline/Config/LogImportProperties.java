package com.pfe.sageline.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "sageline.import")
@Getter
@Setter
public class LogImportProperties {

    private String storageRoot = "storage/logs";
    private DataSize maxFileSize = DataSize.ofMegabytes(2);
    private int snippetLines = 12;

}
