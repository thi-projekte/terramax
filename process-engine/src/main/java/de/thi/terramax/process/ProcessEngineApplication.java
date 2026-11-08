package de.thi.terramax.process;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CIB seven Process Engine für TerraMax. Führt die BPMN-Prozesse aus (zunächst "Besuchstour", #29) und stellt die
 * REST-API unter /engine-rest bereit. Alle .bpmn-Dateien im Classpath werden beim Start automatisch deployt.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProcessEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessEngineApplication.class, args);
    }
}
