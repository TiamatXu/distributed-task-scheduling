package io.github.tiamatxu.distributedtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Component
public class ShellTaskExecutor implements TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ShellTaskExecutor.class);

    @Override
    public String execute(String command) {
        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            logger.info("Executing shell command: {}", command);
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.redirectErrorStream(true); // Redirect error stream to output stream

            process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Wait for the process to complete
            boolean finished = process.waitFor(60, TimeUnit.SECONDS); // Max 60 seconds
            if (!finished) {
                logger.warn("Shell command timed out after 60 seconds: {}", command);
                process.destroyForcibly();
                return "Command timed out.\n" + output.toString();
            }

            int exitCode = process.exitValue();
            logger.info("Shell command '{}' exited with code {}", command, exitCode);

            if (exitCode != 0) {
                logger.error("Shell command failed with exit code {}. Output:\n{}", exitCode, output.toString());
                return "Command failed with exit code " + exitCode + ".\n" + output.toString();
            }

        } catch (Exception e) {
            logger.error("Error executing shell command: {}", command, e);
            return "Error executing command: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return output.toString();
    }
}
