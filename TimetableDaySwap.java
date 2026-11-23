import java.util.List;

/** Builds an option by exchanging two weekdays within one group. */
public final class TimetableDaySwap {
    private TimetableDaySwap() { }

    public static List<TimetableOptionStore.Session> generate(
            List<TimetableOptionStore.Session> source, String groupInput, String dayA, String dayB) {
        String group = TimetableOptionStore.normalizeGroup(groupInput);
        String first = TimetableOptionStore.normalizeDay(dayA);
        String second = TimetableOptionStore.normalizeDay(dayB);
        if (first.equals(second)) throw new IllegalArgumentException("Choose two different days to swap.");
        List<TimetableOptionStore.Session> option = TimetableOptionStore.swapDays(source, group, first, second);
        if (TimetableOptionStore.fingerprint(option).equals(TimetableOptionStore.fingerprint(source))) {
            throw new IllegalArgumentException("Those days produce the original timetable; choose another pair.");
        }
        return option;
    }
}
