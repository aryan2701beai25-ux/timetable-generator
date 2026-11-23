# University timetable lookup

This CSV lookup prototype reads `data/timetable.csv`. A student enters a group (for example, `G8` or `AIML-3-G8`) and a weekday. It prints that group's classes with their times, subjects, instructors, and room. It runs separately from the existing SQLite prototype (`Main.java` and `Database.java`), so you can try the group/day lookup without replacing your database code.

## Run it in VS Code on Windows

1. Open this project folder in VS Code.
2. Choose **Terminal → New Terminal**. Make sure the terminal is at the project folder (the folder containing `TimetableLookup.java` and `data`).
3. Compile and run:

   ```powershell
   javac TimetableLookup.java
   java TimetableLookup
   ```

Run the commands from the project folder so the program can find `data/timetable.csv`.

## Data format

`data/timetable.csv` has one row per continuous class block:

```csv
group,day,start_time,end_time,subject,instructor,room
AIML-3-G8,Monday,16:10,17:50,DP-900,Mr Shikhar,MB-203
```

Times use 24-hour `HH:mm` format. The three blocks shown on each weekday page are 16:10-17:50, 17:50-19:30, and 19:50-21:20. The gap from 19:30 to 19:50 is the break. Keep each block as one row; do not create a separate row for every 50-minute period. This preserves the start and end times shown by the PDF.

To add or correct data, edit the CSV and keep exactly seven comma-separated fields. The current values cover only groups AIML-3-G8 through AIML-3-G16 from the supplied PDF. Saturday has no rows because it is blank in the PDF; the app will say no classes are listed. Groups outside these nine are not included.

## Source date note

The PDF filename says 14-25 September, and its footer says it was generated on 14-09-2026. The lookup asks only for a weekday, so confirm with the university that this timetable is still valid before treating it as current. If schedules change by date, add `valid_from` and `valid_to` fields and ask students for the date as well as the group.

## How this fits the SQLite version

The existing SQLite schema has time slots and scheduled meetings, but it does not store the student group directly on a meeting. For this timetable, the cleanest database model is a `timetable_sessions` table with the same seven fields as the CSV (plus a session ID). Import each CSV row into that table, then query with `WHERE group_name = ? AND day_of_week = ? ORDER BY start_time`. This keeps the PDF's combined class blocks intact and makes the group/day lookup straightforward. Keep the CSV as the reviewable source until its rows have been checked against the university's current schedule.

## Generate alternative timetables

The standalone generator keeps `data/timetable.csv` as the default and writes alternatives to a separate `timetable_options.db` database. It creates the `timetable_options` and `timetable_option_sessions` tables on startup. It supports:

1. Swapping two weekdays for one selected group from G8 through G16.
2. Swapping the time slots of two chosen sessions within one group.

Both operations preserve the course, instructor, room, and weekly session count for every affected class. The default schedule is never overwritten. An unchanged default and duplicate saved alternatives are rejected. The console first asks whether to use the default. A default lookup prints the chosen group and day; a generated change prints the selected group's full week. Saving an alternative is silent.

Run from the project folder in PowerShell:

```powershell
javac -cp ".;sqlite-jdbc-3.53.4.0 (1).jar" TimetableComponents.java TimetableOptionStore.java TimetableDaySwap.java TimetableSessionReorder.java TimetableGenerator.java
java --enable-native-access=ALL-UNNAMED -cp ".;sqlite-jdbc-3.53.4.0 (1).jar" TimetableGenerator
```

The generator uses only the seven-column `data/timetable.csv`; it does not modify `Database.java`, `timetable_data.csv`, or the existing lookup program. For a session reorder, enter the day and start time of each of the two sessions to exchange.

The native-access JVM option allows the bundled SQLite JDBC driver to load its native library without printing the restricted-method warnings on newer Java versions. Use the same option when running `Main`:

```powershell
java --enable-native-access=ALL-UNNAMED -cp ".;sqlite-jdbc-3.53.4.0 (1).jar" Main
```
