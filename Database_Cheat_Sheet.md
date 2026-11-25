# `Database.java` Cheat Sheet

This guide follows the current `Database.java` file from top to bottom. The line ranges below cover the whole file; blank lines only separate declarations or code blocks.

## 1. Imports and database URL — lines 1–6

| Lines | Concept | What it means here |
|---|---|---|
| 1 | `java.io.*` wildcard import | Makes file and character-stream classes available, including `FileInputStream`, `InputStreamReader`, `BufferedReader`, and `IOException`. |
| 2 | `java.sql.*` wildcard import | Makes JDBC classes available, including `Connection`, `DriverManager`, `Statement`, `PreparedStatement`, `ResultSet`, and `SQLException`. |
| 3 | `java.util.*` wildcard import | Makes utility classes available, including `Locale`. |
| 5 | Public class | Declares `Database`, which groups the static methods that manage and query the timetable database. `public` allows other classes, such as `Main`, to use it. |
| 6 | `private static final` constant | `DATABASE_URL` is one shared, unchangeable JDBC URL. `jdbc:sqlite:timetable_generator.db` tells the SQLite JDBC driver which database file to open. |

## 2. Opening a database connection — lines 8–14

`connect()` returns a JDBC `Connection` and declares `throws SQLException` because opening or configuring the database can fail.

- `DriverManager.getConnection(DATABASE_URL)` asks JDBC to find a driver for the URL and open the SQLite database.
- `connection.createStatement()` creates a SQL `Statement` for a fixed command.
- The `try (...)` is **try-with-resources**. It closes the `Statement` automatically when the block ends.
- `PRAGMA foreign_keys = ON` enables SQLite foreign-key checks for this connection.
- The method returns the still-open `Connection`. The method that receives it must close it; the callers in this file use try-with-resources for that.

## 3. Building the relational schema — lines 16–61

`createTables()` stores six SQL data-definition commands in a `String[]`, then runs each command. `CREATE TABLE IF NOT EXISTS` makes setup repeatable: existing tables are left in place instead of causing a “table already exists” error.

| Table | Columns and relational rules |
|---|---|
| `timetable_groups` | `group_code TEXT PRIMARY KEY`: each group code is the table's row identifier. In relational design, a primary key is unique and non-null. |
| `courses` | `course_code TEXT PRIMARY KEY`: each course has a unique code. |
| `instructors` | `instructor_id TEXT PRIMARY KEY`; `name TEXT NOT NULL UNIQUE`: an instructor has a key and a required, unique name. |
| `rooms` | `room_id TEXT PRIMARY KEY`: each room has a unique ID. |
| `time_slots` | `slot_id INTEGER PRIMARY KEY`; day, session number, start and end times are required. `UNIQUE(day_of_week, session_number)` prevents two slot rows from representing the same weekday/session number. |
| `scheduled_meetings` | `meeting_id INTEGER PRIMARY KEY AUTOINCREMENT` creates a generated row ID. Group, course, instructor, room, and slot columns are required foreign keys. `UNIQUE(group_code, slot_id)` prevents a group from having two meetings in one slot. `UNIQUE(room_id, slot_id)` prevents the same room from being booked twice in one slot. |

Other concepts in this block:

- **Relational design:** groups, courses, instructors, rooms, and time slots are stored once in their own tables. `scheduled_meetings` connects them by keys.
- **Foreign keys:** each meeting must refer to an existing group, course, instructor, room, and time slot when SQLite foreign-key enforcement is active.
- **String concatenation:** `+` joins parts of long SQL commands across source lines.
- **Statement and `executeUpdate`:** table-creation SQL changes the database schema, so the code runs it with `Statement.executeUpdate(...)`.
- **Loop:** the enhanced `for` loop runs each SQL string in `queries`.
- Both the connection and statement are automatically closed by try-with-resources.
- SQLite has a legacy behavior where some non-integer primary-key declarations do not enforce `NOT NULL` unless it is written explicitly. This schema relies on these key columns being supplied by the importer.

## 4. Importing the CSV — lines 63–161

### Method contract and resources — lines 63–85

`importTimetableData(String csvFile)` receives a CSV path. It declares `IOException` for file-reading problems and `SQLException` for JDBC problems.

The try-with-resources block opens:

- a JDBC `Connection`;
- a `Statement` for the fixed `DELETE` command;
- a character-reading chain: `FileInputStream` reads file bytes, `InputStreamReader` decodes UTF-8 bytes as characters, and `BufferedReader` reads complete lines efficiently;
- six `PreparedStatement`s for inserting groups, courses, instructors, rooms, time slots, and meetings.

`?` symbols in prepared SQL are parameter placeholders. The CSV values are bound later with typed setters instead of being pasted into SQL text. This separates data from SQL syntax and helps prevent SQL injection.

`connection.setAutoCommit(false)` starts a transaction: the importer can commit all the changes together or roll them back if something fails.

### Read and validate each row — lines 86–127

1. Read the first line as the CSV header. A `null` header means the file is empty, so the method throws `IOException`.
2. `DELETE FROM scheduled_meetings` clears old meeting rows before refresh. The lookup tables are not cleared.
3. `readLine()` returns the next text row, or `null` at end-of-file. The `while` loop continues until it reaches `null`.
4. `lineNumber` tracks the source row for useful validation errors. Blank rows are skipped with `continue`.
5. `line.split(",", -1)` divides the row into fields. The negative limit keeps empty fields at the end of a row, so malformed rows are still detected.
6. The importer requires exactly eight fields, in this order: group code, day, session number, start time, end time, course code, room ID, instructor name.
7. `trim()` removes surrounding spaces. `toUpperCase(Locale.ROOT)` normalizes codes without relying on the computer’s language settings.
8. `normalizeDay(...)` converts an accepted weekday input to its standard spelling.
9. `Integer.parseInt(...)` converts the session number from CSV text to an integer.
10. Session numbers must be 1, 2, or 3. Invalid values produce an `IOException` that includes the CSV line number.
11. The instructor ID is derived from the instructor’s name: uppercase it, then `replaceAll("[^A-Z0-9]+", "_")` replaces each run of non-letter/digit characters with underscores.
12. `dayNumber(day) * 10 + sessionNumber` creates a numeric slot ID. For example, Monday/session 2 becomes `12`.

### Bind and execute inserts — lines 129–149

Each prepared statement is reused for each input row:

- `setString(index, value)` binds text parameters.
- `setInt(index, value)` binds integer parameters.
- Parameter numbering starts at 1 and follows the order of `?` placeholders.
- `executeUpdate()` runs each insert. The lookup rows are inserted before the meeting row, so its foreign keys can refer to them.
- `INSERT OR IGNORE` tells SQLite to skip a row when it violates a uniqueness constraint instead of stopping the import. This is useful for repeated lookup values, but it can also silently skip conflicting timetable rows.

`importedRows++` counts nonblank CSV rows that passed Java-side validation and reached the insert steps; because the SQL uses `OR IGNORE`, that count is not necessarily the number of rows SQLite actually inserted.

### Commit, rollback, and cleanup — lines 151–161

- If the file is read and processed successfully, `connection.commit()` makes the transaction’s changes permanent, then the program prints the number of rows processed.
- The multi-catch handles `IOException`, `SQLException`, and `RuntimeException`. Runtime errors can include invalid integer parsing and other invalid input.
- On an error, `connection.rollback()` discards the uncommitted database changes, then `throw exception` passes the original failure to the caller.
- Try-with-resources closes the reader, prepared statements, ordinary statement, and connection when the method exits, including when an exception occurs.

## 5. Query and print one group/day — lines 163–203

`printDayTimetable(...)` normalizes the requested group and day, then constructs a `SELECT` query.

### SQL concepts — lines 168–177

- `SELECT` chooses the start time, end time, course code, instructor name, and room ID to display.
- Table aliases (`sm`, `ts`, `c`, `i`, `r`) shorten table names in the query.
- `JOIN ... ON ...` links meeting rows to their time slot, course, instructor, and room rows through matching keys.
- `AS instructor_name` gives the selected instructor column a convenient result name.
- `WHERE` filters results by group and day. The `?` placeholders keep those inputs out of the SQL string.
- `ORDER BY ts.session_number` prints sessions in timetable order.

### JDBC result processing — lines 179–203

- `prepareStatement(query)` creates a parameterized query.
- `setString(1, groupCode)` and `setString(2, day)` bind values to the two placeholders.
- `executeQuery()` runs a `SELECT` and returns a `ResultSet` cursor.
- `results.next()` advances the cursor one row at a time and returns `false` when no rows remain.
- `getString(columnName)` reads selected text values by their result-column names.
- `found` records whether any row was returned, allowing a “no entries” message when the result set is empty.
- The nested try-with-resources closes the result set; the outer one closes the prepared statement and connection.

## 6. Detect instructor conflicts — lines 205–235

`printInstructorConflicts()` runs a fixed SQL query and prints any instructor assigned to more than one group in the same slot.

- `GROUP_CONCAT(...)` combines group names from matching rows into one display string.
- `SUBSTR(...)` extracts part of the group code; `INSTR(...)` finds where `-G` begins.
- `GROUP BY sm.instructor_id, sm.slot_id` gathers rows for the same instructor and slot.
- `HAVING COUNT(*) > 1` keeps only groups with more than one meeting row. `HAVING` filters after grouping; `WHERE` filters individual rows before grouping.
- Because this query has no user-supplied values, it uses an ordinary `Statement` rather than a parameterized statement.
- The `ResultSet` loop reads each conflict using `getString(...)` and `getInt(...)`.
- `conflictFound` controls whether the method prints the “no conflicts” message after the loop.

## 7. Input conversion helpers — lines 237–274

These methods are `private static`: they are internal helpers and do not need an object instance.

- `normalizeDay(String dayInput)` trims and lowercases text, requires at least three characters, checks the first three letters against Monday–Friday, and returns the full day name. Invalid input throws `IllegalArgumentException`.
- `normalizeGroup(String groupInput)` trims, uppercases, and removes whitespace. Short inputs such as `G8` become `AIML-3-G8`; other inputs are returned in normalized form.
- `displayGroup(String groupCode)` finds the final `-G` marker with `lastIndexOf`. If present, `substring` returns the short group name; otherwise, it returns the original value.
- `dayNumber(String day)` maps Monday through Thursday to 1–4 and all remaining accepted days (Friday) to 5. The importer uses that number to make `slot_id`.

## 8. Quick JDBC and SQL reference

| Code used | Purpose in this file |
|---|---|
| `DriverManager.getConnection(...)` | Opens the SQLite connection. |
| `Connection` | Represents the active database connection and transaction. |
| `Statement` | Runs fixed SQL such as DDL, `DELETE`, and the conflict query. |
| `PreparedStatement` | Runs parameterized inserts and the group/day query. |
| `setString`, `setInt` | Bind typed values to SQL placeholders. |
| `executeUpdate()` | Runs inserts, deletes, and table-creation statements. |
| `executeQuery()` | Runs `SELECT` statements and returns a result set. |
| `ResultSet` | Cursor over query results. |
| `setAutoCommit(false)`, `commit()`, `rollback()` | Control the CSV import transaction. |
| Try-with-resources | Closes JDBC and file resources automatically. |
| `SQLException`, `IOException` | Represent database and file-reading failures. |

## 9. Whole-file line map

| Lines | Covered section |
|---|---|
| 1–6 | Imports, class declaration, database URL constant |
| 8–14 | Connection creation and SQLite foreign-key setting |
| 16–61 | SQL schema definitions and table-creation loop |
| 63–85 | CSV importer signature, file/JDBC resources, transaction setup |
| 86–127 | Header, delete, row loop, parsing, validation, normalization, slot ID |
| 129–149 | Prepared-statement parameter binding and inserts |
| 151–161 | Commit, row count, rollback, exception propagation, resource cleanup |
| 163–203 | Parameterized group/day query and `ResultSet` printing |
| 205–235 | Instructor-conflict aggregation query and output |
| 237–274 | Day/group display and numeric conversion helpers |
| 275 | Blank separator after the final helper method |
| 276 | Closing brace for the `Database` class |
