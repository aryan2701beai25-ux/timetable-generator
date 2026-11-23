import java.io.IOException;
import java.sql.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            Database.createTables();
            Database.importTimetableData("timetable_data.csv");
            Database.printInstructorConflicts();

            try (Scanner input = new Scanner(System.in)) {
                System.out.print("Enter group: ");
                String groupCode = input.nextLine();

                System.out.print("Enter day: ");
                String day = input.nextLine();

                Database.printDayTimetable(groupCode, day);
            }
        } catch (IOException | SQLException | IllegalArgumentException exception) {
            System.out.println("The timetable could not be loaded or displayed.");
            exception.printStackTrace();
        }
    }
}
