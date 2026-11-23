import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;

/** Shared timetable model, CSV reader, and SQLite storage for generated options. */
final class TimetableOptionStore {
    static final String SOURCE = "data/timetable.csv";
    private static final String DB_URL = DatabaseUrl.OPTION_DATABASE;
    static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

    static final class Session {
        final String group, day, start, end, subject, instructor, room;

        Session(String group, String day, String start, String end,
                String subject, String instructor, String room) {
            this.group = group;
            this.day = day;
            this.start = start;
            this.end = end;
            this.subject = subject;
            this.instructor = instructor;
            this.room = room;
        }

        Session at(String newDay, String newStart, String newEnd) {
            return new Session(group, newDay, newStart, newEnd, subject, instructor, room);
        }

        String key() {
            return String.join("|", group, day, start, end, subject, instructor, room);
        }
    }

    private TimetableOptionStore() { }

    static List<Session> readDefault() throws IOException {
        List<Session> sessions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(SOURCE), "UTF-8"))) {
            String header = reader.readLine();
            if (!CsvHeaderRules.isTimetableHeader(header)) {
                throw new IOException("Unexpected timetable CSV header in " + SOURCE);
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                try {
                    Session session = SessionFactory.from(CsvRowParser.parse(line, lineNumber));
                    if (!session.group.matches("AIML-3-G(8|9|1[0-6])")) {
                        throw new IllegalArgumentException("Group is outside G8-G16");
                    }
                    sessions.add(session);
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid timetable value on CSV line " + lineNumber, exception);
                }
            }
        }
        if (sessions.isEmpty()) throw new IOException("No timetable rows were found in " + SOURCE);
        sessions.sort(new TimetableSessionOrder());
        return sessions;
    }

    static String normalizeDay(String input) {
        return WeekdayRules.normalize(input);
    }

    static String normalizeTime(String input) {
        return SessionTimeRules.normalize(input);
    }

    static String normalizeGroup(String input) {
        return GroupCodeRules.normalize(input);
    }

    static void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String query : OptionSchemaSql.createStatements()) statement.executeUpdate(query);
        }
    }

    private static Connection connect() throws SQLException {
        return DatabaseConnectionFactory.open(DB_URL);
    }

    static boolean save(String name, String operation, List<Session> sessions)
            throws SQLException {
        String fingerprint = fingerprint(sessions);
        OptionMetadata metadata = new OptionMetadata(name, operation, fingerprint);
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement option = connection.prepareStatement(
                    "INSERT OR IGNORE INTO timetable_options(option_name, operation, fingerprint) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                option.setString(1, metadata.name);
                option.setString(2, metadata.operation);
                option.setString(3, metadata.fingerprint);
                if (option.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                int id;
                try (ResultSet keys = option.getGeneratedKeys()) {
                    if (keys.next()) id = keys.getInt(1);
                    else try (Statement query = connection.createStatement();
                              ResultSet result = query.executeQuery("SELECT last_insert_rowid()")) {
                        result.next(); id = result.getInt(1);
                    }
                }
                try (PreparedStatement row = connection.prepareStatement(
                        "INSERT INTO timetable_option_sessions VALUES(?,?,?,?,?,?,?,?)")) {
                    for (Session session : sessions) {
                        SessionRowBinder.bind(row, id, session);
                    }
                    row.executeBatch();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    static List<Session> swapDays(List<Session> source, String group,
            String firstDay, String secondDay) {
        return DaySwapRule.apply(source, group, firstDay, secondDay);
    }

    static List<Session> swapSlots(List<Session> source, int firstIndex, int secondIndex) {
        return SessionSlotSwapRule.apply(source, firstIndex, secondIndex);
    }

    static String fingerprint(List<Session> sessions) {
        return TimetableFingerprint.calculate(sessions);
    }

    static void printDay(List<Session> sessions, String groupInput, String dayInput) {
        String group = normalizeGroup(groupInput);
        String day = normalizeDay(dayInput);
        TimetableOutputPrinter.printDay(sessions, group, day);
    }

    static void printDays(List<Session> sessions, String groupInput,
            String firstDayInput, String secondDayInput) {
        ChangedDays days = new ChangedDays(firstDayInput, secondDayInput);
        printDay(sessions, groupInput, days.first);
        if (!days.first.equals(days.second)) printDay(sessions, groupInput, days.second);
    }
}
