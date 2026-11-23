import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class TimetableLookup {
    private static final String DATA_FILE = "data/timetable.csv";

    public static void main(String[] args) {
        List<Session> sessions;
        try {
            sessions = loadSessions(DATA_FILE);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Could not load " + DATA_FILE + ": " + e.getMessage());
            System.out.println("Run this program from the project folder so it can find data/timetable.csv.");
            return;
        }

        System.out.println("University timetable lookup");
        System.out.println("The PDF includes groups AIML-3-G8 through AIML-3-G16.");
        System.out.println("Source file is labeled 14-25 Sep; confirm its validity dates before sharing as a current timetable.");

        try (Scanner input = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nEnter group (for example G8 or AIML-3-G8; blank to quit): ");
                String group = normalizeGroup(input.nextLine());
                if (group.isEmpty()) {
                    break;
                }

                System.out.print("Enter day (for example Monday or Mon): ");
                String day = normalizeDay(input.nextLine());
                if (day == null) {
                    System.out.println("Please enter a weekday name, such as Monday. Saturday is also accepted.");
                    continue;
                }

                final String requestedGroup = group;
                final String requestedDay = day;
                List<Session> matches = new ArrayList<>();
                boolean groupExists = false;
                for (Session session : sessions) {
                    if (session.group.equalsIgnoreCase(requestedGroup)) {
                        groupExists = true;
                        if (session.day.equalsIgnoreCase(requestedDay)) {
                            matches.add(session);
                        }
                    }
                }

                if (matches.isEmpty()) {
                    if (!groupExists) {
                        System.out.println("That group is not in this PDF. Available groups: AIML-3-G8 to AIML-3-G16.");
                    } else {
                        System.out.println("No classes are listed for " + requestedGroup + " on " + requestedDay + ".");
                    }
                    continue;
                }

                System.out.println("\n" + requestedGroup + " - " + requestedDay);
                System.out.println("------------------------------------------------------------");
                for (Session session : matches) {
                    System.out.printf("%s - %s | %s | %s | %s%n",
                            displayTime(session.start),
                            displayTime(session.end),
                            session.subject,
                            session.instructor,
                            session.room);
                }
            }
        }

        System.out.println("Goodbye.");
    }

    private static List<Session> loadSessions(String file) throws IOException {
        List<Session> sessions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split(",", -1);
                if (fields.length != 7) {
                    throw new IllegalArgumentException("Expected 7 columns on CSV line " + lineNumber);
                }
                try {
                    sessions.add(new Session(
                            fields[0].trim(),
                            fields[1].trim(),
                            fields[2].trim(),
                            fields[3].trim(),
                            fields[4].trim(),
                            fields[5].trim(),
                            fields[6].trim()));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("Invalid value on CSV line " + lineNumber, e);
                }
            }
        }
        return sessions;
    }

    private static String normalizeGroup(String input) {
        String group = input.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (group.matches("G\\d+")) {
            return "AIML-3-" + group;
        }
        return group;
    }

    private static String normalizeDay(String input) {
        String day = input.trim().toLowerCase(Locale.ROOT);
        if (day.length() < 3) {
            return null;
        }
        String prefix = day.substring(0, 3);
        switch (prefix) {
            case "mon": return "Monday";
            case "tue": return "Tuesday";
            case "wed": return "Wednesday";
            case "thu": return "Thursday";
            case "fri": return "Friday";
            case "sat": return "Saturday";
            case "sun": return "Sunday";
            default: return null;
        }
    }

    private static String displayTime(String time) {
        int hour = Integer.parseInt(time.substring(0, 2));
        String minute = time.substring(3, 5);
        String suffix = hour < 12 ? "AM" : "PM";
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return displayHour + ":" + minute + " " + suffix;
    }

    private static class Session {
        private final String group;
        private final String day;
        private final String start;
        private final String end;
        private final String subject;
        private final String instructor;
        private final String room;

        private Session(String group, String day, String start, String end,
                        String subject, String instructor, String room) {
            this.group = group;
            this.day = day;
            this.start = start;
            this.end = end;
            this.subject = subject;
            this.instructor = instructor;
            this.room = room;
        }
    }
}
