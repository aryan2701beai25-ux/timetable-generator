 import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class Database {
    private static final String DATABASE_URL = "jdbc:sqlite:timetable_generator.db";

    public static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(DATABASE_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void createTables() throws SQLException {
        String[] queries = {
            "CREATE TABLE IF NOT EXISTS timetable_groups ("
                + "group_code TEXT PRIMARY KEY)",

            "CREATE TABLE IF NOT EXISTS courses ("
                + "course_code TEXT PRIMARY KEY)",

            "CREATE TABLE IF NOT EXISTS instructors ("
                + "instructor_id TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL UNIQUE)",

            "CREATE TABLE IF NOT EXISTS rooms ("
                + "room_id TEXT PRIMARY KEY)",

            "CREATE TABLE IF NOT EXISTS time_slots ("
                + "slot_id INTEGER PRIMARY KEY, "
                + "day_of_week TEXT NOT NULL, "
                + "session_number INTEGER NOT NULL, "
                + "start_time TEXT NOT NULL, "
                + "end_time TEXT NOT NULL, "
                + "UNIQUE (day_of_week, session_number))",

            "CREATE TABLE IF NOT EXISTS scheduled_meetings ("
                + "meeting_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "group_code TEXT NOT NULL, "
                + "course_code TEXT NOT NULL, "
                + "instructor_id TEXT NOT NULL, "
                + "room_id TEXT NOT NULL, "
                + "slot_id INTEGER NOT NULL, "
                + "FOREIGN KEY (group_code) REFERENCES timetable_groups(group_code), "
                + "FOREIGN KEY (course_code) REFERENCES courses(course_code), "
                + "FOREIGN KEY (instructor_id) REFERENCES instructors(instructor_id), "
                + "FOREIGN KEY (room_id) REFERENCES rooms(room_id), "
                + "FOREIGN KEY (slot_id) REFERENCES time_slots(slot_id), "
                + "UNIQUE (group_code, slot_id), "
                + "UNIQUE (room_id, slot_id))"
        };

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            for (String query : queries) {
                statement.executeUpdate(query);
            }
        }
    }

    public static void importTimetableData(String csvFile)
            throws IOException, SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(new FileInputStream(csvFile), "UTF-8"));
             PreparedStatement groupInsert = connection.prepareStatement(
                 "INSERT OR IGNORE INTO timetable_groups (group_code) VALUES (?)");
             PreparedStatement courseInsert = connection.prepareStatement(
                 "INSERT OR IGNORE INTO courses (course_code) VALUES (?)");
             PreparedStatement instructorInsert = connection.prepareStatement(
                 "INSERT OR IGNORE INTO instructors (instructor_id, name) VALUES (?, ?)");
             PreparedStatement roomInsert = connection.prepareStatement(
                 "INSERT OR IGNORE INTO rooms (room_id) VALUES (?)");
             PreparedStatement slotInsert = connection.prepareStatement(
                 "INSERT OR IGNORE INTO time_slots "
                     + "(slot_id, day_of_week, session_number, start_time, end_time) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement meetingInsert = connection.prepareStatement(
                 "INSERT OR IGNORE INTO scheduled_meetings "
                     + "(group_code, course_code, instructor_id, room_id, slot_id) VALUES (?, ?, ?, ?, ?)")) {

            connection.setAutoCommit(false);

            try {
                String header = reader.readLine();
                if (header == null) {
                    throw new IOException("The timetable CSV is empty.");
                }

                statement.executeUpdate("DELETE FROM scheduled_meetings");

                String line;
                int lineNumber = 1;
                int importedRows = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] values = line.split(",", -1);
                    if (values.length != 8) {
                        throw new IOException(
                            "CSV line " + lineNumber + " should have 8 values.");
                    }

                    String groupCode = values[0].trim().toUpperCase(Locale.ROOT);
                    String day = normalizeDay(values[1]);
                    int sessionNumber = Integer.parseInt(values[2].trim());
                    String startTime = values[3].trim();
                    String endTime = values[4].trim();
                    String courseCode = values[5].trim().toUpperCase(Locale.ROOT);
                    String roomId = values[6].trim().toUpperCase(Locale.ROOT);
                    String instructorName = values[7].trim();

                    if (sessionNumber < 1 || sessionNumber > 3) {
                        throw new IOException(
                            "Invalid session number on CSV line " + lineNumber + ".");
                    }

                    String instructorId = instructorName
                        .toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]+", "_");
                    int slotId = dayNumber(day) * 10 + sessionNumber;

                    groupInsert.setString(1, groupCode);
                    groupInsert.executeUpdate();
                    courseInsert.setString(1, courseCode);
                    courseInsert.executeUpdate();
                    instructorInsert.setString(1, instructorId);
                    instructorInsert.setString(2, instructorName);
                    instructorInsert.executeUpdate();
                    roomInsert.setString(1, roomId);
                    roomInsert.executeUpdate();
                    slotInsert.setInt(1, slotId);
                    slotInsert.setString(2, day);
                    slotInsert.setInt(3, sessionNumber);
                    slotInsert.setString(4, startTime);
                    slotInsert.setString(5, endTime);
                    slotInsert.executeUpdate();
                    meetingInsert.setString(1, groupCode);
                    meetingInsert.setString(2, courseCode);
                    meetingInsert.setString(3, instructorId);
                    meetingInsert.setString(4, roomId);
                    meetingInsert.setInt(5, slotId);
                    meetingInsert.executeUpdate();

                    importedRows++;
                }

                connection.commit();
                System.out.println("Timetable refreshed from CSV. Rows read: " + importedRows);
            } catch (IOException | SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public static void printDayTimetable(String groupInput, String dayInput)
            throws SQLException {
        String groupCode = normalizeGroup(groupInput);
        String day = normalizeDay(dayInput);

        String query = "SELECT ts.start_time, ts.end_time, c.course_code, "
            + "i.name AS instructor_name, r.room_id "
            + "FROM scheduled_meetings sm "
            + "JOIN time_slots ts ON sm.slot_id = ts.slot_id "
            + "JOIN courses c ON sm.course_code = c.course_code "
            + "JOIN instructors i ON sm.instructor_id = i.instructor_id "
            + "JOIN rooms r ON sm.room_id = r.room_id "
            + "WHERE sm.group_code = ? "
            + "AND ts.day_of_week = ? "
            + "ORDER BY ts.session_number";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, groupCode);
            statement.setString(2, day);
            try (ResultSet results = statement.executeQuery()) {

            boolean found = false;
            System.out.println("\n" + displayGroup(groupCode) + " timetable for " + day + ":");

            while (results.next()) {
                found = true;
                System.out.println(
                    results.getString("start_time") + "-"
                        + results.getString("end_time") + " | "
                        + results.getString("course_code") + " | "
                        + results.getString("instructor_name") + " | Room "
                        + results.getString("room_id"));
            }

            if (!found) {
                System.out.println("No timetable entries found for that group and day.");
            }
            }
        }
    }

    public static void printInstructorConflicts() throws SQLException {
        String query = "SELECT i.name AS instructor_name, ts.day_of_week, "
            + "ts.session_number, "
            + "GROUP_CONCAT(SUBSTR(sm.group_code, INSTR(sm.group_code, '-G') + 1), ', ') "
            + "AS groups_involved "
            + "FROM scheduled_meetings sm "
            + "JOIN instructors i ON sm.instructor_id = i.instructor_id "
            + "JOIN time_slots ts ON sm.slot_id = ts.slot_id "
            + "GROUP BY sm.instructor_id, sm.slot_id "
            + "HAVING COUNT(*) > 1";

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(query)) {

            boolean conflictFound = false;
            while (results.next()) {
                conflictFound = true;
                System.out.println(
                    "Source timetable conflict: "
                        + results.getString("instructor_name") + " on "
                        + results.getString("day_of_week") + " session "
                        + results.getInt("session_number") + " is listed for groups "
                        + results.getString("groups_involved"));
            }

            if (!conflictFound) {
                System.out.println("No instructor time conflicts found.");
            }
        }
    }

    private static String normalizeDay(String dayInput) {
        String day = dayInput.trim().toLowerCase(Locale.ROOT);
        if (day.length() < 3) {
            throw new IllegalArgumentException("Enter a weekday from Monday to Friday.");
        }
        String prefix = day.substring(0, 3);
        if (prefix.equals("mon")) return "Monday";
        if (prefix.equals("tue")) return "Tuesday";
        if (prefix.equals("wed")) return "Wednesday";
        if (prefix.equals("thu")) return "Thursday";
        if (prefix.equals("fri")) return "Friday";
        throw new IllegalArgumentException(
            "Enter a weekday from Monday to Friday.");
    }

    private static String normalizeGroup(String groupInput) {
        String group = groupInput.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (group.matches("G\\d+")) {
            return "AIML-3-" + group;
        }
        return group;
    }

    private static String displayGroup(String groupCode) {
        int groupStart = groupCode.lastIndexOf("-G");
        if (groupStart >= 0) {
            return groupCode.substring(groupStart + 1);
        }
        return groupCode;
    }

    private static int dayNumber(String day) {
        if (day.equals("Monday")) return 1;
        if (day.equals("Tuesday")) return 2;
        if (day.equals("Wednesday")) return 3;
        if (day.equals("Thursday")) return 4;
        return 5;
    }

}
