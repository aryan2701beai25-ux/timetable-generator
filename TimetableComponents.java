import java.io.IOException;
import java.sql.*;
import java.util.*;



class GroupCodeRules {
    static String normalize(String input) {
        String group = input.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (group.matches("G\\d+")) group = "AIML-3-" + group;
        if (!group.matches("AIML-3-G(8|9|1[0-6])")) {
            throw new IllegalArgumentException("Choose a group from G8 through G16.");
        }
        return group;
    }
}

class WeekdayRules {
    static String normalize(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.length() < 3) throw new IllegalArgumentException("Enter a weekday from Monday to Friday.");
        for (String day : TimetableOptionStore.DAYS) {
            if (day.toLowerCase(Locale.ROOT).startsWith(text)) return day;
        }
        throw new IllegalArgumentException("Enter a weekday from Monday to Friday.");
    }
}

class SessionTimeRules {
    static String normalize(String input) {
        String time = input.trim();
        if (!time.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException("Enter time as HH:mm.");
        int hour = Integer.parseInt(time.substring(0, 2));
        int minute = Integer.parseInt(time.substring(3, 5));
        if (hour > 23 || minute > 59) throw new IllegalArgumentException("Enter a valid time as HH:mm.");
        return time;
    }
}

class CsvHeaderRules {
    static boolean isTimetableHeader(String header) {
        return header != null && header.trim().equalsIgnoreCase(
                "group,day,start_time,end_time,subject,instructor,room");
    }
}

class CsvRow {
    final String[] fields;
    CsvRow(String[] fields) { this.fields = fields; }
}

class CsvRowParser {
    static CsvRow parse(String line, int lineNumber) throws IOException {
        String[] fields = line.split(",", -1);
        if (fields.length != 7) throw new IOException("Expected 7 columns on CSV line " + lineNumber);
        return new CsvRow(fields);
    }
}

class SessionFactory {
    static TimetableOptionStore.Session from(CsvRow row) {
        String[] fields = row.fields;
        return new TimetableOptionStore.Session(fields[0].trim(), WeekdayRules.normalize(fields[1]),
                SessionTimeRules.normalize(fields[2]), SessionTimeRules.normalize(fields[3]),
                fields[4].trim(), fields[5].trim(), fields[6].trim());
    }
}

class WeekdayOrder {
    static int index(String day) {
        for (int i = 0; i < TimetableOptionStore.DAYS.length; i++) {
            if (TimetableOptionStore.DAYS[i].equals(day)) return i;
        }
        return TimetableOptionStore.DAYS.length;
    }
}

class TimetableSessionOrder implements Comparator<TimetableOptionStore.Session> {
    public int compare(TimetableOptionStore.Session first, TimetableOptionStore.Session second) {
        int groupOrder = first.group.compareTo(second.group);
        if (groupOrder != 0) return groupOrder;
        int dayOrder = WeekdayOrder.index(first.day) - WeekdayOrder.index(second.day);
        if (dayOrder != 0) return dayOrder;
        return first.start.compareTo(second.start);
    }
}

class TimetableFingerprint {
    static String calculate(List<TimetableOptionStore.Session> sessions) {
        return sessions.stream().map(TimetableOptionStore.Session::key)
                .sorted().reduce((first, second) -> first + "\n" + second).orElse("");
    }
}

class DaySwapRule {
    static List<TimetableOptionStore.Session> apply(List<TimetableOptionStore.Session> source,
            String group, String firstDay, String secondDay) {
        List<TimetableOptionStore.Session> result = new ArrayList<>();
        for (TimetableOptionStore.Session session : source) {
            String day = session.day;
            if (session.group.equals(group) && day.equals(firstDay)) day = secondDay;
            else if (session.group.equals(group) && day.equals(secondDay)) day = firstDay;
            result.add(session.at(day, session.start, session.end));
        }
        return result;
    }
}

class SessionSlotSwapRule {
    static List<TimetableOptionStore.Session> apply(List<TimetableOptionStore.Session> source,
            int firstIndex, int secondIndex) {
        List<TimetableOptionStore.Session> result = new ArrayList<>(source);
        TimetableOptionStore.Session first = source.get(firstIndex);
        TimetableOptionStore.Session second = source.get(secondIndex);
        result.set(firstIndex, first.at(second.day, second.start, second.end));
        result.set(secondIndex, second.at(first.day, first.start, first.end));
        return result;
    }
}

class OptionNameFactory {
    static String daySwap(String group, String dayA, String dayB) {
        return "Day swap " + group + " " + dayA + " / " + dayB;
    }
    static String sessionReorder(String group, String day, String start) {
        return "Session reorder " + group + " " + day + " " + start;
    }
}

class DatabaseUrl {
    static final String OPTION_DATABASE = "jdbc:sqlite:timetable_options.db";
}

class DatabaseConnectionFactory {
    static Connection open(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }
}

class OptionSchemaSql {
    static String[] createStatements() {
        return new String[] {
            "CREATE TABLE IF NOT EXISTS timetable_options ("
                + "option_id INTEGER PRIMARY KEY AUTOINCREMENT, option_name TEXT NOT NULL, "
                + "operation TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "fingerprint TEXT NOT NULL UNIQUE)",
            "CREATE TABLE IF NOT EXISTS timetable_option_sessions ("
                + "option_id INTEGER NOT NULL, group_code TEXT NOT NULL, day TEXT NOT NULL, "
                + "start_time TEXT NOT NULL, end_time TEXT NOT NULL, subject TEXT NOT NULL, "
                + "instructor TEXT NOT NULL, room TEXT NOT NULL, FOREIGN KEY(option_id) "
                + "REFERENCES timetable_options(option_id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_option_group_day ON "
                + "timetable_option_sessions(option_id, group_code, day, start_time)"
        };
    }
}

class OptionMetadata {
    final String name, operation, fingerprint;
    OptionMetadata(String name, String operation, String fingerprint) {
        this.name = name; this.operation = operation; this.fingerprint = fingerprint;
    }
}

class SessionRowBinder {
    static void bind(PreparedStatement row, int optionId,
            TimetableOptionStore.Session session) throws SQLException {
        row.setInt(1, optionId); row.setString(2, session.group); row.setString(3, session.day);
        row.setString(4, session.start); row.setString(5, session.end);
        row.setString(6, session.subject); row.setString(7, session.instructor);
        row.setString(8, session.room); row.addBatch();
    }
}

class TimetableOutputFormatter {
    static String format(TimetableOptionStore.Session session) {
        return String.format("%s-%s | %-12s | %-22s | %s", session.start,
                session.end, session.subject, session.instructor, session.room);
    }
}

class TimetableOutputPrinter {
    static void printDay(List<TimetableOptionStore.Session> sessions, String group, String day) {
        List<TimetableOptionStore.Session> matches = new ArrayList<>();
        for (TimetableOptionStore.Session session : sessions) {
            if (session.group.equals(group) && session.day.equals(day)) matches.add(session);
        }
        matches.sort(Comparator.comparing(session -> session.start));
        System.out.println("\n" + group + " - " + day);
        System.out.println("------------------------------------------------------------");
        if (matches.isEmpty()) {
            System.out.println("No classes listed for this day.");
            return;
        }
        System.out.printf("%-13s | %-12s | %-22s | %s%n", "TIME", "SUBJECT", "INSTRUCTOR", "ROOM");
        for (TimetableOptionStore.Session session : matches) {
            System.out.println(TimetableOutputFormatter.format(session));
        }
    }
}

class TimetablePrompt {
    static String ask(Scanner input, String prompt) {
        System.out.print(prompt);
        return input.nextLine();
    }
}

class DefaultDayRequest {
    final String group, day;
    DefaultDayRequest(String group, String day) { this.group = group; this.day = day; }
}

class ChangedDays {
    final String first, second;
    ChangedDays(String first, String second) {
        this.first = WeekdayRules.normalize(first);
        this.second = WeekdayRules.normalize(second);
    }
}
