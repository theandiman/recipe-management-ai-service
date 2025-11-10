package com.recipe.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;

@SpringBootApplication
public class RecipeGeneratorServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RecipeGeneratorServiceApplication.class);
    // write the PID to a predictable file so external scripts can manage the process
    // use the JVM temporary directory (java.io.tmpdir) as the default location
    String tmp = System.getProperty("java.io.tmpdir");
    String pidFile = java.nio.file.Paths.get(tmp, "backend.pid").toString();
    app.addListeners(new ApplicationPidFileWriter(pidFile));

    // lightweight startup log so CI/ops can see which PID file was chosen
    System.out.println("PID file: " + pidFile);

    app.run(args);
    }
}
