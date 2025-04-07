package org.example;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@Data
@ConfigurationProperties(prefix = "client")
@ConfigurationPropertiesScan
public class PropertyHolder {
    private FederationPropertyHolder federation;
}


