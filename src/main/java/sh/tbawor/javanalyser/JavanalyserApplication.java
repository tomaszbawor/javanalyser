package sh.tbawor.javanalyser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JavanalyserApplication {
  public static void main(String[] args) {
    SpringApplication.run(JavanalyserApplication.class, args);
  }
}
