import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Console flow for default timetable lookup and group-specific alternatives. */
public class TimetableGenerator {
    public static void main(String[] args) {
        try {
            TimetableOptionStore.initialize();
            List<TimetableOptionStore.Session> defaultSchedule = TimetableOptionStore.readDefault();
            try (Scanner input = new Scanner(System.in)) {
                String useDefault = TimetablePrompt.ask(input,
                        "Do you want the default timetable? (yes/no): ").trim();

                if (useDefault.equalsIgnoreCase("yes") || useDefault.equalsIgnoreCase("y")) {
                    String group = TimetableOptionStore.normalizeGroup(
                            TimetablePrompt.ask(input, "Enter group (G8-G16): "));
                    String day = TimetablePrompt.ask(input, "Enter day: ");
                    DefaultDayRequest request = new DefaultDayRequest(group, day);
                    TimetableOptionStore.printDay(defaultSchedule, request.group, request.day);
                    return;
                }
                if (!useDefault.equalsIgnoreCase("no") && !useDefault.equalsIgnoreCase("n")) {
                    throw new IllegalArgumentException("Enter yes or no.");
                }

                String group = TimetableOptionStore.normalizeGroup(
                        TimetablePrompt.ask(input, "Enter group to change (G8-G16): "));
                System.out.println("1. Swap two days");
                System.out.println("2. Reorder two sessions");
                String choice = TimetablePrompt.ask(input, "Choose a change: ").trim();
                List<TimetableOptionStore.Session> option;

                if (choice.equals("1")) {
                    String dayA = TimetablePrompt.ask(input, "First day to swap: ");
                    String dayB = TimetablePrompt.ask(input, "Swap with day: ");
                    option = TimetableDaySwap.generate(defaultSchedule, group, dayA, dayB);
                    TimetableOptionStore.save(OptionNameFactory.daySwap(group, dayA, dayB),
                            "day-swap", option);
                    TimetableOptionStore.printDays(option, group, dayA, dayB);
                } else if (choice.equals("2")) {
                    String dayA = TimetablePrompt.ask(input, "First session day: ");
                    String startA = TimetablePrompt.ask(input, "First session start time (HH:mm): ");
                    String dayB = TimetablePrompt.ask(input, "Second session day: ");
                    String startB = TimetablePrompt.ask(input, "Second session start time (HH:mm): ");
                    option = TimetableSessionReorder.generate(
                            defaultSchedule, group, dayA, startA, dayB, startB);
                    TimetableOptionStore.save(OptionNameFactory.sessionReorder(group, dayA, startA),
                            "session-reorder", option);
                    TimetableOptionStore.printDays(option, group, dayA, dayB);
                } else {
                    throw new IllegalArgumentException("Choose 1 or 2.");
                }
            }
        } catch (IOException | SQLException | IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        }
    }
}
