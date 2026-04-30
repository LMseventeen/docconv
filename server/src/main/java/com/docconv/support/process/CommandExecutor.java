package com.docconv.support.process;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CommandExecutor {

    private Logger log = null;

    public CommandExecutor() {
    }

    public CommandExecutor(Logger log) {
        this.log = log;
    }

    public String exec(List<String> cmd) {
        return exec(cmd, null, null);
    }

    public String exec(List<String> cmd, Duration timeout) {
        return exec(cmd, null, timeout);
    }

    public String exec(List<String> cmd, String workingDirectory) {
        return exec(cmd, workingDirectory, null);
    }

    public String exec(List<String> cmd, String workingDirectory, Duration timeout) {
        var realCmd = new ArrayList<String>();
        var maskedCmd = new ArrayList<String>();

        for (var c : cmd) {
            if (c.startsWith("******")) {
                realCmd.add(c.substring(6));
                maskedCmd.add("******");
            } else {
                realCmd.add(c);
                maskedCmd.add(c);
            }
        }

        if (Objects.nonNull(log)) {
            var joiner = new StringJoiner(" ");
            maskedCmd.forEach(joiner::add);
            log.info("exec: {}", joiner);
        }

        var output = new StringBuilder();
        Process p = null;
        var thread = Executors.newSingleThreadExecutor();
        try {
            var pb = new ProcessBuilder(realCmd);
            pb.redirectErrorStream(true);
            if (!Objects.isNull(workingDirectory)) {
                pb.directory(new File(workingDirectory));
            }

            p = pb.start();

            Process finalP = p;
            var outputFuture = thread.submit(() -> {
                try {
                    var reader = new BufferedReader(new InputStreamReader(finalP.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (Objects.nonNull(log)) {
                            output.append(line);
                            log.info(line);
                        }
                    }
                } catch (IOException e) {
                    if (Objects.nonNull(log)) {
                        log.error("read output error: {}", e.getMessage());
                    }
                }
            });

            if (Objects.isNull(timeout)) {
                p.waitFor();
            } else {
                var finished = p.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    p.destroy();
                    throw new RuntimeException("exec timeout.");
                }
            }

            outputFuture.get();

            var exitCode = p.exitValue();
            if (exitCode != 0) {
                log.error("exec failed, exit code: {}", exitCode);
                throw new RuntimeException("exec failed, exit code: " + exitCode);
            }

            if (Objects.nonNull(log)) {
                log.info("exec finished.");
            }

            return output.toString();
        } catch (IOException | InterruptedException | ExecutionException e) {
            if (Objects.nonNull(log)) {
                log.error("exec failed: {}", e.getMessage());
            }
            throw new RuntimeException("exec failed: " + e.getMessage());
        } finally {
            thread.shutdown();
            if (Objects.nonNull(p) && p.isAlive()) {
                p.destroy();
            }
        }
    }
}
