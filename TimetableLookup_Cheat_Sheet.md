# `TimetableLookup.java` Cheat Sheet

This guide follows the current `TimetableLookup.java` from top to bottom. Its line map covers the complete 165-line file, including the nested `Session` class.

## 1. Imports and class setup — lines 1–5

| Lines | Concept | Purpose in this file |
|---|---|---|
| 1 | `java.io.*` wildcard import | Provides `FileInputStream`, `InputStreamReader`, `BufferedReader`, and `IOException` for reading the CSV as UTF-8 text. |
| 2 | `java.util.*` wildcard import | Provides `List`, `ArrayList`, `Locale`, and `Scanner`. |
| 4 | Public class declaration | Defines `TimetableLookup`, the standalone console lookup program. |
| 5 | `private static final` constant | `DATA_FILE` holds the relative path to `data/timetable.csv`. `private` limits access to this class, `static` shares it across the class, and `final` prevents reassignment. |

## 2. Program entry point and CSV load — lines 7–20

`main(String[] args)` is the Java entry point. The `String[] args` parameter can receive command-line arguments, although this program does not use them.

- `List<Session> sessions` declares a generic list that will hold timetable rows as `Session` objects.
- `loadSessions(DATA_FILE)` reads the source CSV.
- The `catch (IOException | IllegalArgumentException e)` is a **multi-catch**. It handles either file-reading errors or invalid CSV shape/value errors.
- `e.getMessage()` adds the error explanation to the console message.
- `return` ends the program if loading fails, so the lookup does not continue with missing data.
- Lines 17–19 print the application heading, the groups expected in the PDF, and a reminder about the source timetable’s validity dates.

## 3. Read requests and search the timetable — lines 21–72

### Input loop — lines 21–34

- `new Scanner(System.in)` reads keyboard input. Try-with-resources closes the scanner when the block ends.
- `while (true)` keeps accepting lookups until the user submits a blank group.
- `nextLine()` reads a complete input line.
- `normalizeGroup(...)` cleans up group input. An empty result means the user entered a blank line; `break` exits the loop.
- `normalizeDay(...)` converts a weekday abbreviation/name to a standard day. If it returns `null`, the input was not recognized; the program prints a message and `continue` starts the next lookup iteration.

### Search and distinguish missing group/day — lines 36–56

- `final String requestedGroup` and `requestedDay` preserve the normalized query values for the search.
- `new ArrayList<>()` creates an initially empty result list.
- `groupExists` records whether any CSV row belongs to the requested group.
- The enhanced `for` loop checks every `Session` in the loaded list.
- `equalsIgnoreCase(...)` compares text without case sensitivity.
- A matching group sets `groupExists` to `true`; a matching day adds that session to `matches`.
- If `matches` is empty, a nested `if` distinguishes an unknown group from a valid group with no classes on that day.
- `continue` returns to the top of the input loop after either message.

### Print matches — lines 58–72

- The heading shows the normalized group and day; the separator improves console readability.
- A second enhanced `for` loop prints every matching session.
- `System.out.printf(...)` uses `%s` placeholders for values and `%n` for a platform-appropriate newline.
- `displayTime(...)` converts each start/end time from 24-hour to 12-hour display.
- Subject, instructor, and room are read directly from the `Session` object.
- After the user exits the loop, `Goodbye.` is printed.

## 4. Load and map CSV rows — lines 74–108

`loadSessions(String file)` returns a list of sessions and declares `throws IOException` because file reading can fail.

### File and character streams — lines 74–80

- `new ArrayList<Session>()` creates the destination list. The generic type ensures only `Session` objects are added.
- `FileInputStream` reads the file as bytes.
- `InputStreamReader(..., "UTF-8")` decodes those bytes into characters using UTF-8.
- `BufferedReader` wraps the character reader and provides efficient line-by-line input.
- Try-with-resources closes all wrapped file streams when the block ends.
- `readLine()` returns one line or `null` at end-of-file; the `while` condition stops at `null`.

### Parse and validate each row — lines 78–105

1. `lineNumber` starts at zero and increments for each line read.
2. The first line is skipped as the CSV header. The code does not verify the header’s text.
3. Blank/whitespace-only lines are skipped using `trim().isEmpty()`.
4. `split(",", -1)` separates a row into fields and preserves empty fields at the end.
5. The program requires exactly seven values: group, day, start time, end time, subject, instructor, and room.
6. If the field count is wrong, `IllegalArgumentException` is thrown with the line number.
7. Each field is trimmed and passed to the `Session` constructor in the CSV column order.
8. The `catch (RuntimeException e)` wraps a runtime error with a message identifying the CSV line and preserves the original exception as its cause.
9. Each successfully constructed session is added to the list.

**CSV parsing limit:** `split(",", -1)` is a simple comma split. It does not handle quoted fields that contain commas, such as `"Smith, Jane"`.

## 5. Normalize group input — lines 110–116

`normalizeGroup(String input)` prepares group text for matching:

- `trim()` removes leading/trailing spaces.
- `toUpperCase(Locale.ROOT)` makes the code uppercase consistently regardless of machine locale.
- `replaceAll("\\s+", "")` removes all whitespace inside the input.
- `matches("G\\d+")` checks whether the entire value is `G` followed by one or more digits. For example, `G8` becomes `AIML-3-G8`.
- Full group names remain unchanged after normalization. This method does not strictly validate that the group is between G8 and G16; unsupported values simply produce no matches later.

## 6. Normalize day input — lines 118–134

`normalizeDay(String input)` returns a standard day name or `null`:

- Trims and lowercases input with `Locale.ROOT`.
- Requires at least three characters; shorter values return `null`.
- `substring(0, 3)` takes the first three letters.
- The `switch` maps `mon`, `tue`, `wed`, `thu`, `fri`, `sat`, and `sun` to full day names.
- `default: return null` marks unrecognized input.
- Saturday and Sunday are accepted as day names even though the supplied timetable CSV may have no sessions for them.

## 7. Format a time for display — lines 136–143

`displayTime(String time)` converts a 24-hour `HH:mm` string into a 12-hour string with AM/PM.

- `substring(0, 2)` reads the hour; `Integer.parseInt(...)` converts it to an integer.
- `substring(3, 5)` reads the minutes as text so a leading zero is retained.
- The ternary expression chooses `AM` before noon and `PM` from noon onward.
- `% 12` converts the hour to a 12-hour clock. Since midnight/noon map to zero, the `if` changes zero to 12.
- The return expression concatenates the hour, colon, minutes, and suffix.

This method expects a correctly shaped time like `16:10`; it does not validate input length or ranges first. Invalid CSV time values can therefore cause a runtime error during display.

## 8. Store one timetable row — lines 145–165

`Session` is a `private static` nested class. It is private to `TimetableLookup`, and static means it does not need an instance of the outer `TimetableLookup` class.

- The seven `private final String` fields store group, day, start, end, subject, instructor, and room.
- `final` means each field is assigned once and cannot later be reassigned.
- The constructor receives those seven values and assigns them using `this.field = parameter`.
- The class is a simple Java-object mapping of one CSV record. The other methods read its fields to match and display timetable entries.
- The last braces close the nested class and then the outer class.

## 9. Quick concepts reference

| Concept | How this file uses it |
|---|---|
| Generics | `List<Session>` and `ArrayList<Session>` restrict the list to `Session` objects. |
| Character/file I/O | Byte stream → UTF-8 character stream → buffered line reader. |
| Exception handling | Multi-catch for load errors; row-level runtime error wrapping; error messages shown to the user. |
| Control flow | `while`, enhanced `for`, `if/else`, `switch`, `break`, and `continue`. |
| String processing | `trim`, `toUpperCase`, `toLowerCase`, `replaceAll`, `matches`, `split`, `substring`, and case-insensitive comparison. |
| Object mapping | CSV columns are stored in a `Session` object. |
| Console I/O | `Scanner` reads requests; `System.out.println` and `printf` display results. |
| Resource management | Try-with-resources closes the scanner and reader/stream chain. |

## 10. Whole-file line map

| Lines | Covered section |
|---|---|
| 1–5 | Imports, class declaration, CSV path constant |
| 7–20 | Main method, CSV load call, load-error handling, introductory messages |
| 21–34 | Scanner, repeat lookup loop, group/day input and invalid-day handling |
| 36–56 | Search, group-existence check, no-match messages |
| 58–72 | Timetable output, loop exit, goodbye message |
| 74–80 | CSV-loading method, generic list, file/character streams, line reader |
| 81–88 | Line numbering, header skip, blank-line skip |
| 89–105 | CSV split, field-count validation, Session construction, exception wrapping |
| 106–108 | Resource block ends and loaded list returns |
| 110–116 | Group normalization |
| 118–134 | Day normalization with switch |
| 136–143 | 24-hour to 12-hour time formatting |
| 145–164 | Nested `Session` class, fields, constructor |
| 165 | Closing brace for `TimetableLookup` |
