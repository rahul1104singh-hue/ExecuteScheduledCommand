import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Scheduler {

    abstract static class ScheduledCommand {
        String command;
        abstract boolean shouldRun(LocalDateTime now);
        abstract void markExecuted();
    }

    static class OneTimeCommand extends ScheduledCommand {
        LocalDateTime scheduledTime;
        boolean executed = false;

        OneTimeCommand(LocalDateTime time, String cmd) {
            this.scheduledTime = time;
            this.command = cmd;
        }

        @Override
        boolean shouldRun(LocalDateTime now) {
            return !executed && now.equals(scheduledTime);
        }

        @Override
        void markExecuted() {
            executed = true;
        }
    }

    static class RecurringCommand extends ScheduledCommand {
        int intervalMinutes;

        RecurringCommand(int interval, String cmd) {
            this.intervalMinutes = interval;
            this.command = cmd;
        }

        @Override
        boolean shouldRun(LocalDateTime now) {
            return now.getMinute() % intervalMinutes == 0;
        }

        @Override
        void markExecuted() { }
    }

    private static List<ScheduledCommand> loadCommands(String filePath) throws IOException {
        List<ScheduledCommand> commands = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("*/")) {
                String[] parts = line.split("\\s+", 2);
                int interval = Integer.parseInt(parts[0].substring(2));
                commands.add(new RecurringCommand(interval, parts[1]));
            } else {
                String[] parts = line.split("\\s+", 6);
                int minute = Integer.parseInt(parts[0]);
                int hour = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                int month = Integer.parseInt(parts[3]);
                int year = Integer.parseInt(parts[4]);
                String cmd = parts[5];
                LocalDateTime time = LocalDateTime.of(year, month, day, hour, minute);
                commands.add(new OneTimeCommand(time, cmd));
            }
        }
        return commands;
    }

    private static void executeCommand(ScheduledCommand cmd, BufferedWriter logWriter) {
        try {
            Process process;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                process = new ProcessBuilder("cmd.exe", "/c", cmd.command).start();
            } else {
                process = new ProcessBuilder("bash", "-c", cmd.command).start();
            }

            process.waitFor();

            String output = new String(process.getInputStream().readAllBytes());
            String error = new String(process.getErrorStream().readAllBytes());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            logWriter.write("[" + timestamp + "] " + cmd.command + "\n");
            if (!output.isEmpty()) logWriter.write(output + "\n");
            if (!error.isEmpty()) logWriter.write("ERROR: " + error + "\n");
            logWriter.flush();

            cmd.markExecuted();
        } catch (Exception e) {
            try {
                logWriter.write("Execution failed for: " + cmd.command + " -> " + e.getMessage() + "\n");
                logWriter.flush();
            } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) throws Exception {
        String commandsFile;

        if (args.length > 0) {
            commandsFile = args[0]; // path from argument
        } else {
            commandsFile = "tmp/commands.txt"; // default relative path
        }

        String logFile = "sample-output.txt";

        List<ScheduledCommand> commands = loadCommands(commandsFile);
        System.out.println("Loaded " + commands.size() + " commands.");

        try (BufferedWriter logWriter = new BufferedWriter(new FileWriter(logFile, true))) {
            while (true) {
                LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
                for (ScheduledCommand cmd : commands) {
                    if (cmd.shouldRun(now)) {
                        System.out.println("Running: " + cmd.command + " at " + now);
                        executeCommand(cmd, logWriter);
                    }
                }
                Thread.sleep(60_000); // sleep 1 minute
            }
        }
    }
}
