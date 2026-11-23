import java.util.*;

/** Builds an option by exchanging the time slots of two sessions in one group. */
public final class TimetableSessionReorder {
    private TimetableSessionReorder() { }

    public static List<TimetableOptionStore.Session> generate(
            List<TimetableOptionStore.Session> source, String groupInput,
            String dayA, String startA, String dayB, String startB) {
        String group = TimetableOptionStore.normalizeGroup(groupInput);
        String firstDay = TimetableOptionStore.normalizeDay(dayA);
        String secondDay = TimetableOptionStore.normalizeDay(dayB);
        int first = find(source, group, firstDay, startA);
        int second = find(source, group, secondDay, startB);
        if (first == second) throw new IllegalArgumentException("Choose two different sessions.");
        List<TimetableOptionStore.Session> option = TimetableOptionStore.swapSlots(source, first, second);
        if (TimetableOptionStore.fingerprint(option).equals(TimetableOptionStore.fingerprint(source))) {
            throw new IllegalArgumentException("That selection produces the original timetable.");
        }
        return option;
    }

    private static int find(List<TimetableOptionStore.Session> source,
            String group, String day, String startInput) {
        String start = TimetableOptionStore.normalizeTime(startInput);
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            TimetableOptionStore.Session session = source.get(i);
            if (session.group.equals(group) && session.day.equals(day) && session.start.equals(start)) {
                matches.add(i);
            }
        }
        if (matches.isEmpty()) throw new IllegalArgumentException(
                "No session found for " + group + " on " + day + " at " + start + ".");
        return matches.get(0);
    }
}
