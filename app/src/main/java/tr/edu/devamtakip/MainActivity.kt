package tr.edu.devamtakip

import android.app.DatePickerDialog
import android.content.ContentResolver
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

private val dayNames = mapOf(
    1 to "Pazartesi", 2 to "Salı", 3 to "Çarşamba", 4 to "Perşembe", 5 to "Cuma"
)
private val dayShortNames = mapOf(1 to "Pzt", 2 to "Sal", 3 to "Çar", 4 to "Per", 5 to "Cum")
private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("tr", "TR"))

private data class Student(val studentNo: String, val firstName: String, val lastName: String) {
    val fullName: String get() = "$firstName $lastName".trim()
}

private data class AppConfig(
    val id: Long = 0L,
    val courseName: String = "",
    val unit: String = "Bolvadin MYO",
    val startDate: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val midtermWeek: Int = 7,
    val hoursByDay: Map<Int, Int> = (1..5).associateWith { 0 }
)

private data class AbsenceRecord(val courseId: Long, val studentNo: String, val date: LocalDate, val hour: Int)
private data class StudentSummary(
    val student: Student,
    val scheduledHours: Int,
    val absentHours: Int
) {
    val attendedHours: Int get() = (scheduledHours - absentHours).coerceAtLeast(0)
    val attendancePercent: Int get() = if (scheduledHours == 0) 0 else ((attendedHours * 100.0) / scheduledHours).toInt()
}

private enum class ReportFormat { PDF, EXCEL }

private data class ReportData(
    val summaries: List<StudentSummary>,
    val absences: List<AbsenceRecord>,
    val expectedHoursPerStudent: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AttendanceDb(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AttendanceApp(db)
                }
            }
        }
    }
}

private class AttendanceDb(context: Context) : SQLiteOpenHelper(context, "devam_takip.db", null, 2) {
    private val prefs = context.getSharedPreferences("course_config", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE students(student_no TEXT PRIMARY KEY NOT NULL, first_name TEXT NOT NULL, last_name TEXT NOT NULL)")
        createCoursesTable(db)
        createAbsencesTable(db)
    }

    private fun createCoursesTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS courses(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "course_name TEXT NOT NULL, unit TEXT NOT NULL, start_epoch_day INTEGER NOT NULL, " +
                "midterm_week INTEGER NOT NULL, hours_1 INTEGER NOT NULL DEFAULT 0, " +
                "hours_2 INTEGER NOT NULL DEFAULT 0, hours_3 INTEGER NOT NULL DEFAULT 0, " +
                "hours_4 INTEGER NOT NULL DEFAULT 0, hours_5 INTEGER NOT NULL DEFAULT 0)"
        )
    }

    private fun createAbsencesTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS absences(" +
                "student_no TEXT NOT NULL, course_id INTEGER NOT NULL, attendance_date TEXT NOT NULL, " +
                "hour_no INTEGER NOT NULL, PRIMARY KEY(student_no, course_id, attendance_date, hour_no))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_absences_date ON absences(course_id, attendance_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createCoursesTable(db)

            // Migrate the previous single-course settings into the new course list.
            val oldName = prefs.getString("course_name", "")?.trim().orEmpty()
            var migratedCourseId = 0L
            if (oldName.isNotBlank()) {
                val defaultStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val start = runCatching { LocalDate.ofEpochDay(prefs.getLong("start_epoch_day", defaultStart.toEpochDay())) }.getOrDefault(defaultStart)
                val values = android.content.ContentValues().apply {
                    put("course_name", oldName)
                    put("unit", "Bolvadin MYO")
                    put("start_epoch_day", start.toEpochDay())
                    put("midterm_week", prefs.getInt("midterm_week", 7).coerceIn(1, 15))
                    (1..5).forEach { day -> put("hours_$day", prefs.getInt("hours_$day", 0).coerceIn(0, 8)) }
                }
                migratedCourseId = db.insert("courses", null, values)
            }

            db.execSQL("DROP INDEX IF EXISTS idx_absences_date")
            db.execSQL("ALTER TABLE absences RENAME TO absences_v1")
            createAbsencesTable(db)
            db.execSQL(
                "INSERT INTO absences(student_no, course_id, attendance_date, hour_no) " +
                    "SELECT student_no, $migratedCourseId, attendance_date, hour_no FROM absences_v1"
            )
            db.execSQL("DROP TABLE absences_v1")
        }
    }

    fun loadCourses(): List<AppConfig> {
        val result = mutableListOf<AppConfig>()
        readableDatabase.rawQuery(
            "SELECT id, course_name, unit, start_epoch_day, midterm_week, hours_1, hours_2, hours_3, hours_4, hours_5 FROM courses ORDER BY course_name COLLATE NOCASE, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val defaultStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val start = runCatching { LocalDate.ofEpochDay(c.getLong(3)) }.getOrDefault(defaultStart)
                val hours = (1..5).associateWith { day -> c.getInt(4 + day).coerceIn(0, 8) }
                result += AppConfig(
                    id = c.getLong(0),
                    courseName = c.getString(1),
                    unit = c.getString(2),
                    startDate = start,
                    midtermWeek = c.getInt(4).coerceIn(1, 15),
                    hoursByDay = hours
                )
            }
        }
        return result
    }

    fun saveCourse(config: AppConfig): AppConfig {
        val values = android.content.ContentValues().apply {
            put("course_name", config.courseName.trim())
            put("unit", config.unit)
            put("start_epoch_day", config.startDate.toEpochDay())
            put("midterm_week", config.midtermWeek.coerceIn(1, 15))
            (1..5).forEach { day -> put("hours_$day", (config.hoursByDay[day] ?: 0).coerceIn(0, 8)) }
        }
        val db = writableDatabase
        val id = if (config.id > 0L) {
            val updated = db.update("courses", values, "id=?", arrayOf(config.id.toString()))
            if (updated > 0) config.id else db.insertOrThrow("courses", null, values)
        } else {
            db.insertOrThrow("courses", null, values)
        }
        return config.copy(id = id)
    }

    fun deleteCourse(courseId: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("absences", "course_id=?", arrayOf(courseId.toString()))
            writableDatabase.delete("courses", "id=?", arrayOf(courseId.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun loadStudents(): List<Student> {
        val result = mutableListOf<Student>()
        readableDatabase.rawQuery("SELECT student_no, first_name, last_name FROM students ORDER BY student_no COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) result += Student(c.getString(0), c.getString(1), c.getString(2))
        }
        return result
    }

    fun upsertStudents(students: List<Student>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            students.forEach { s ->
                val values = android.content.ContentValues().apply {
                    put("student_no", s.studentNo.trim())
                    put("first_name", s.firstName.trim())
                    put("last_name", s.lastName.trim())
                }
                db.insertWithOnConflict("students", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Returns the new state: true means the selected hour is now marked absent. */
    fun toggleAbsence(courseId: Long, studentNo: String, date: LocalDate, hour: Int): Boolean {
        val db = writableDatabase
        val dateText = date.toString()
        val current = isAbsent(courseId, studentNo, date, hour)
        if (current) {
            db.delete(
                "absences",
                "student_no=? AND course_id=? AND attendance_date=? AND hour_no=?",
                arrayOf(studentNo, courseId.toString(), dateText, hour.toString())
            )
            return false
        }
        val values = android.content.ContentValues().apply {
            put("student_no", studentNo)
            put("course_id", courseId)
            put("attendance_date", dateText)
            put("hour_no", hour)
        }
        db.insertWithOnConflict("absences", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    /** Mark every scheduled hour as absent, or clear all absence marks for this student/date. */
    fun setAllHoursAbsent(courseId: Long, studentNo: String, date: LocalDate, hours: Int, absent: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (absent) {
                for (hour in 1..hours.coerceAtLeast(0)) {
                    val values = android.content.ContentValues().apply {
                        put("student_no", studentNo)
                        put("course_id", courseId)
                        put("attendance_date", date.toString())
                        put("hour_no", hour)
                    }
                    db.insertWithOnConflict("absences", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            } else {
                db.delete(
                    "absences",
                    "student_no=? AND course_id=? AND attendance_date=?",
                    arrayOf(studentNo, courseId.toString(), date.toString())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun isAbsent(courseId: Long, studentNo: String, date: LocalDate, hour: Int): Boolean {
        return readableDatabase.rawQuery(
            "SELECT 1 FROM absences WHERE course_id=? AND student_no=? AND attendance_date=? AND hour_no=? LIMIT 1",
            arrayOf(courseId.toString(), studentNo, date.toString(), hour.toString())
        ).use { cursor -> cursor.moveToFirst() }
    }

    fun getAbsences(courseId: Long? = null, studentNo: String? = null): List<AbsenceRecord> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (courseId != null) { conditions += "course_id=?"; args += courseId.toString() }
        if (studentNo != null) { conditions += "student_no=?"; args += studentNo }
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        val result = mutableListOf<AbsenceRecord>()
        readableDatabase.rawQuery(
            "SELECT course_id, student_no, attendance_date, hour_no FROM absences$where ORDER BY attendance_date, hour_no, student_no",
            if (args.isEmpty()) null else args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                val date = runCatching { LocalDate.parse(c.getString(2)) }.getOrNull() ?: continue
                result += AbsenceRecord(c.getLong(0), c.getString(1), date, c.getInt(3))
            }
        }
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceApp(db: AttendanceDb) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf(db.loadCourses()) }
    var selectedCourseId by remember { mutableStateOf(courses.firstOrNull()?.id) }
    var editingCourse by remember { mutableStateOf<AppConfig?>(null) }
    var students by remember { mutableStateOf(db.loadStudents()) }
    var selectedTab by remember { mutableIntStateOf(if (courses.isEmpty()) 0 else 1) }
    var attendanceRevision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    var selectedReportStudent by remember { mutableStateOf<String?>(null) }
    var pendingPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExcelBytes by remember { mutableStateOf<ByteArray?>(null) }
    val selectedCourse = courses.firstOrNull { it.id == selectedCourseId } ?: courses.firstOrNull()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val imported = withContext(Dispatchers.IO) {
                        val filename = queryDisplayName(context.contentResolver, uri).lowercase(Locale.ROOT)
                        val input = context.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı.")
                        input.use { parseStudentFile(filename, it) }
                    }
                    if (imported.isEmpty()) error("Dosyada öğrenci bulunamadı. Sütunlar Öğrenci No ve Adı Soyadı ya da Öğrenci No, Ad, Soyad olmalı.")
                    withContext(Dispatchers.IO) { db.upsertStudents(imported) }
                    students = withContext(Dispatchers.IO) { db.loadStudents() }
                    notice = "${imported.size} öğrenci aktarıldı. Mevcut numaralar güncellendi, yeni numaralar eklendi."
                }.onFailure { e -> notice = "Aktarım yapılamadı: ${e.message ?: "Dosya biçimini kontrol edin."}" }
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = pendingPdfBytes ?: error("PDF raporu oluşturulamadı.")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Dosya kaydedilemedi.")
                notice = "PDF raporu başarıyla kaydedildi."
            }.onFailure { notice = "PDF raporu kaydedilemedi: ${it.message ?: "Bilinmeyen hata"}" }
        }
    }

    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = pendingExcelBytes ?: error("Excel raporu oluşturulamadı.")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Dosya kaydedilemedi.")
                notice = "Excel (.xlsx) raporu başarıyla kaydedildi."
            }.onFailure { notice = "Excel raporu kaydedilemedi: ${it.message ?: "Bilinmeyen hata"}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(selectedCourse?.courseName?.ifBlank { "Devam Takip" } ?: "Devam Takip", fontWeight = FontWeight.SemiBold)
                    if (selectedCourse != null) Text("${selectedCourse.unit} · ${students.size} öğrenci", style = MaterialTheme.typography.labelSmall)
                    else if (students.isNotEmpty()) Text("${students.size} öğrenci", style = MaterialTheme.typography.labelSmall)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { editingCourse = null; selectedTab = 0; notice = "" },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ders Ekle") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; notice = "" },
                    icon = { androidx.compose.material3.Icon(Icons.Default.MenuBook, contentDescription = null) },
                    label = { Text("Derslerim") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; notice = "" },
                    icon = { androidx.compose.material3.Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text("Yoklama") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3; notice = "" },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Assessment, contentDescription = null) },
                    label = { Text("Raporlar") }
                )
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (notice.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(notice, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            when (selectedTab) {
                0 -> SetupScreen(
                    config = editingCourse,
                    students = students,
                    onSave = { next ->
                        val saved = db.saveCourse(next)
                        courses = db.loadCourses()
                        selectedCourseId = saved.id
                        editingCourse = null
                        selectedTab = 1
                        notice = "${saved.courseName} dersi kaydedildi ve Derslerim listesine eklendi."
                    },
                    onImport = { importLauncher.launch(arrayOf("*/*")) }
                )
                1 -> CoursesScreen(
                    courses = courses,
                    selectedCourseId = selectedCourse?.id,
                    studentCount = students.size,
                    onSelect = { id -> selectedCourseId = id },
                    onAdd = { editingCourse = null; selectedTab = 0 },
                    onEdit = { course -> editingCourse = course; selectedCourseId = course.id; selectedTab = 0 },
                    onTakeAttendance = { id -> selectedCourseId = id; selectedTab = 2 },
                    onDelete = { course ->
                        db.deleteCourse(course.id)
                        courses = db.loadCourses()
                        selectedCourseId = courses.firstOrNull()?.id
                        notice = "${course.courseName} dersi silindi. Bu derse ait yoklama kayıtları da silindi."
                    }
                )
                2 -> if (selectedCourse != null) {
                    AttendanceScreen(
                        config = selectedCourse,
                        students = students,
                        db = db,
                        revision = attendanceRevision,
                        onToggle = { attendanceRevision++; notice = "Yoklama kaydedildi." }
                    )
                } else {
                    EmptyState("Yoklama almak için önce Ders Ekle bölümünden ders tanımlayın.")
                }
                else -> if (selectedCourse != null) {
                    ReportScreen(
                        config = selectedCourse,
                        students = students,
                        db = db,
                        selectedStudentNo = selectedReportStudent,
                        revision = attendanceRevision,
                        onSelectStudent = { selectedReportStudent = it },
                        onExport = { studentNo, format ->
                            scope.launch {
                                notice = "Rapor hazırlanıyor…"
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        when (format) {
                                            ReportFormat.PDF -> buildReportPdf(selectedCourse, students, db, studentNo)
                                            ReportFormat.EXCEL -> buildReportXlsx(selectedCourse, students, db, studentNo)
                                        }
                                    }
                                }.onSuccess { bytes ->
                                    when (format) {
                                        ReportFormat.PDF -> {
                                            pendingPdfBytes = bytes
                                            pdfExportLauncher.launch(reportFilename(selectedCourse, studentNo, "pdf"))
                                        }
                                        ReportFormat.EXCEL -> {
                                            pendingExcelBytes = bytes
                                            excelExportLauncher.launch(reportFilename(selectedCourse, studentNo, "xlsx"))
                                        }
                                    }
                                }.onFailure { error ->
                                    notice = "Rapor oluşturulamadı: ${error.message ?: "Bilinmeyen hata"}"
                                }
                            }
                        }
                    )
                } else {
                    EmptyState("Rapor görüntülemek için önce ders ekleyin.")
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    config: AppConfig?,
    students: List<Student>,
    onSave: (AppConfig) -> Unit,
    onImport: () -> Unit
) {
    var courseName by remember(config) { mutableStateOf(config?.courseName.orEmpty()) }
    var unit by remember(config) { mutableStateOf(config?.unit ?: "Bolvadin MYO") }
    var startDate by remember(config) { mutableStateOf(config?.startDate ?: LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var midtermWeek by remember(config) { mutableIntStateOf(config?.midtermWeek ?: 7) }
    var hoursByDay by remember(config) { mutableStateOf(config?.hoursByDay?.toMap() ?: (1..5).associateWith { 0 }) }
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (config == null) "Yeni ders ekle" else "Ders ayarlarını düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = courseName,
            onValueChange = { courseName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ders adı") },
            singleLine = true
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Birim", fontWeight = FontWeight.SemiBold)
            UnitDropdown(value = unit, onSelected = { unit = it })
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. haftanın başlangıcı", fontWeight = FontWeight.SemiBold)
                Text("Seçilen tarih ilgili haftanın pazartesi gününe göre ayarlanır.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            startDate = LocalDate.of(year, month + 1, day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        },
                        startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
                    ).show()
                }) {
                    Text("Başlangıç: ${startDate.format(dateFormatter)} (Pazartesi)")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Haftalık ders programı", fontWeight = FontWeight.SemiBold)
                Text("Ders yapılan günleri seçin, her gün için saat sayısını belirleyin.", style = MaterialTheme.typography.bodySmall)
                (1..5).forEach { day ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = (hoursByDay[day] ?: 0) > 0,
                            onClick = {
                                val updated = hoursByDay.toMutableMap()
                                updated[day] = if ((updated[day] ?: 0) > 0) 0 else 1
                                hoursByDay = updated
                            },
                            label = { Text(dayNames[day] ?: "") }
                        )
                        Spacer(Modifier.width(10.dp))
                        if ((hoursByDay[day] ?: 0) > 0) {
                            Text("Saat:")
                            Spacer(Modifier.width(6.dp))
                            IntDropdown(
                                value = hoursByDay[day] ?: 1,
                                values = (1..8).toList(),
                                display = { "$it" },
                                onSelected = { value -> hoursByDay = hoursByDay.toMutableMap().apply { put(day, value) } }
                            )
                        } else {
                            Text("Ders yok", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val weeklyHours = hoursByDay.values.sum()
                Divider()
                Text("Haftalık toplam: $weeklyHours saat", fontWeight = FontWeight.Medium)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vize haftası", fontWeight = FontWeight.SemiBold)
                Text("14 normal ders haftasına ek olarak toplam 15 haftadan hangisinin vize haftası olacağını seçin.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Takvim haftası:")
                    Spacer(Modifier.width(10.dp))
                    IntDropdown(
                        value = midtermWeek,
                        values = (1..15).toList(),
                        display = { week -> if (week == midtermWeek) "Hafta $week · Vize" else "Hafta $week" },
                        onSelected = { midtermWeek = it }
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Öğrenci listesi", fontWeight = FontWeight.SemiBold)
                Text("Kayıtlı öğrenci: ${students.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Excel sütunları Öğrenci No ve Adı Soyadı ya da Öğrenci No, Ad, Soyad olabilir. .xlsx veya .csv dosyaları desteklenir.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Excel / CSV listesinden öğrenci aktar") }
                if (students.isNotEmpty()) {
                    Divider()
                    Text("Aktarılan öğrenci kayıtları", fontWeight = FontWeight.SemiBold)
                    students.take(8).forEach { student ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(student.studentNo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (students.size > 8) Text("İlk 8 kayıt gösteriliyor. Diğer ${students.size - 8} öğrenci de Yoklama ekranında listelenir.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(
            onClick = {
                onSave(AppConfig(config?.id ?: 0L, courseName.trim(), unit, startDate, midtermWeek, (1..5).associateWith { (hoursByDay[it] ?: 0).coerceIn(0, 8) }))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = courseName.isNotBlank() && hoursByDay.values.any { it > 0 }
        ) { Text(if (config == null) "Ders ayarlarını kaydet" else "Ders değişikliklerini kaydet") }
        Text("Not: Devamsızlık kayıtları bu telefonda saklanır. Her dersin yoklama kayıtları birbirinden ayrı tutulur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UnitDropdown(value: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Bolvadin MYO", "BUB Fakültesi").forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun CoursesScreen(
    courses: List<AppConfig>,
    selectedCourseId: Long?,
    studentCount: Int,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AppConfig) -> Unit,
    onTakeAttendance: (Long) -> Unit,
    onDelete: (AppConfig) -> Unit
) {
    var courseToDelete by remember { mutableStateOf<AppConfig?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Derslerim", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Kayıtlı ders sayısı: ${courses.size} · Öğrenci sayısı: $studentCount", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Yeni ders ekle") }
        if (courses.isEmpty()) {
            EmptyState("Henüz ders eklenmedi. Yeni ders ekle düğmesini kullanarak ders adı, birim ve haftalık programı kaydedin.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(courses, key = { it.id }) { course ->
                    val weekHours = course.hoursByDay.values.sum()
                    Card(
                        Modifier.fillMaxWidth().clickable { onSelect(course.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (course.id == selectedCourseId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(course.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Birim: ${course.unit}", style = MaterialTheme.typography.bodyMedium)
                            Text("Haftalık $weekHours saat · Vize haftası: ${course.midtermWeek}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                (1..5).filter { (course.hoursByDay[it] ?: 0) > 0 }
                                    .joinToString(" · ") { "${dayShortNames[it]}: ${course.hoursByDay[it]} saat" }
                                    .ifBlank { "Haftalık program tanımlanmamış" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { onTakeAttendance(course.id) }, modifier = Modifier.weight(1f)) { Text("Yoklama al") }
                                OutlinedButton(onClick = { onEdit(course) }, modifier = Modifier.weight(1f)) { Text("Düzenle") }
                                OutlinedButton(onClick = { courseToDelete = course }) { Text("Sil") }
                            }
                        }
                    }
                }
            }
        }
    }
    val deleteCandidate = courseToDelete
    if (deleteCandidate != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("Ders silinsin mi?") },
            text = { Text("${deleteCandidate.courseName} ve bu derse ait yoklama kayıtları silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                Button(onClick = { onDelete(deleteCandidate); courseToDelete = null }) { Text("Sil") }
            },
            dismissButton = { OutlinedButton(onClick = { courseToDelete = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun IntDropdown(
    value: Int,
    values: List<Int>,
    display: (Int) -> String,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(display(value)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun AttendanceScreen(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    revision: Int,
    onToggle: () -> Unit
) {
    // Opening Yoklama first shows only the 15-week selector. The selected week
    // opens a separate roster view with the selected course's active weekdays.
    var selectedWeek by remember(config.id) { mutableIntStateOf(0) }
    var selectedDayState by remember(config.id) {
        mutableIntStateOf(config.hoursByDay.entries.firstOrNull { it.value > 0 }?.key ?: 1)
    }
    val availableDays = config.hoursByDay.filterValues { it > 0 }.keys.sorted()
    val selectedDay = selectedDayState.takeIf { it in availableDays } ?: (availableDays.firstOrNull() ?: 1)
    val weekStart = if (selectedWeek in 1..15) config.startDate.plusWeeks((selectedWeek - 1).toLong()) else config.startDate
    val date = weekStart.plusDays((selectedDay - 1).toLong())
    val hours = config.hoursByDay[selectedDay] ?: 0
    @Suppress("UNUSED_VARIABLE") val forceRefresh = revision

    if (selectedWeek == 0) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Yoklama al", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(config.courseName, style = MaterialTheme.typography.titleMedium)
            Text(config.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Hafta seçin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Bir haftaya dokunduğunuzda o haftanın öğrenci yoklama ekranı açılır.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items((1..15).toList(), key = { it }) { week ->
                    val start = config.startDate.plusWeeks((week - 1).toLong())
                    val end = start.plusDays(6)
                    val isMidterm = week == config.midtermWeek
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedWeek = week },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMidterm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    if (isMidterm) "Hafta $week · VİZE" else "Hafta $week",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("${start.format(dateFormatter)} – ${end.format(dateFormatter)}", style = MaterialTheme.typography.bodySmall)
                                val dayLine = availableDays.joinToString(" · ") { day ->
                                    val dayDate = start.plusDays((day - 1).toLong())
                                    "${dayShortNames[day]} ${dayDate.format(dateFormatter)}"
                                }
                                if (dayLine.isNotBlank()) Text(dayLine, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Aç ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { selectedWeek = 0 }, modifier = Modifier.fillMaxWidth()) {
            Text("← Haftalara dön")
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (selectedWeek == config.midtermWeek) "Hafta $selectedWeek · VİZE" else "Hafta $selectedWeek",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(config.courseName, style = MaterialTheme.typography.titleMedium)
        Text(config.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (availableDays.isEmpty()) {
            EmptyState("Bu derse ait haftalık ders saati tanımlanmamış. Derslerim bölümünden dersi düzenleyin.")
            return
        }

        Spacer(Modifier.height(6.dp))
        Text("Ders günü", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableDays.forEach { day ->
                FilterChip(
                    selected = selectedDay == day,
                    onClick = { selectedDayState = day },
                    label = { Text(dayNames[day] ?: "") }
                )
            }
        }
        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${date.format(dateFormatter)} · ${dayNames[selectedDay]}", fontWeight = FontWeight.SemiBold)
                Text("$hours ders saati", style = MaterialTheme.typography.bodyMedium)
                Text("Saat düğmelerinde Var/Yok durumunu değiştirebilirsiniz. ‘Tüm saatleri seç’ hepsini Yok, ‘Tüm saatleri kaldır’ hepsini Var yapar.", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (students.isEmpty()) {
            EmptyState("Öğrenci listesi boş. Ders Ekle bölümünden Excel veya CSV dosyanızı aktarın.")
        } else if (hours > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Öğrenci No", fontWeight = FontWeight.SemiBold)
                    Text("Adı Soyadı", style = MaterialTheme.typography.labelSmall)
                }
                Text("Saat / durum", fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(students, key = { it.studentNo }) { student ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(student.studentNo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (student.fullName.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        db.setAllHoursAbsent(config.id, student.studentNo, date, hours, absent = true)
                                        onToggle()
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Tüm saatleri seç", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        db.setAllHoursAbsent(config.id, student.studentNo, date, hours, absent = false)
                                        onToggle()
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("Tüm saatleri kaldır", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            (1..hours).toList().chunked(4).forEach { hourRow ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    hourRow.forEach { hour ->
                                        val absent = db.isAbsent(config.id, student.studentNo, date, hour)
                                        Button(
                                            onClick = { db.toggleAbsence(config.id, student.studentNo, date, hour); onToggle() },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(9.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (absent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = if (absent) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                if (absent) "$hour · Yok" else "$hour · Var",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    repeat(4 - hourRow.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportScreen(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    selectedStudentNo: String?,
    revision: Int,
    onSelectStudent: (String?) -> Unit,
    onExport: (String?, ReportFormat) -> Unit
) {
    val reportData = remember(config, students, selectedStudentNo, revision) {
        loadReportData(config, students, db, selectedStudentNo)
    }
    val rows = reportData.summaries
    val selected = rows.firstOrNull { it.student.studentNo == selectedStudentNo }
    val allReportData = remember(config, students, revision) { loadReportData(config, students, db, null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Devam raporları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Ders: ${config.courseName} · ${config.unit}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        if (selected != null) {
            OutlinedButton(onClick = { onSelectStudent(null) }) { Text("← Toplu rapora dön") }
            Spacer(Modifier.height(5.dp))
            StudentSummaryCard(selected)
            ReportExportButtons(
                onPdf = { onExport(selected.student.studentNo, ReportFormat.PDF) },
                onExcel = { onExport(selected.student.studentNo, ReportFormat.EXCEL) }
            )
            Text("Devamsızlık yapılan ders saatleri", fontWeight = FontWeight.SemiBold)
            val detail = reportData.absences.filter { it.studentNo == selected.student.studentNo }
            if (detail.isEmpty()) {
                EmptyState("Bu öğrenci için bu derste devamsızlık kaydı yok.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(detail, key = { "${it.courseId}-${it.date}-${it.hour}" }) { absence ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(absence.date.format(dateFormatter), fontWeight = FontWeight.Medium)
                                    Text(dayNames[absence.date.dayOfWeek.value] ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${absence.hour}. ders saati devamsız", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        } else {
            val totalScheduledAllStudents = allReportData.expectedHoursPerStudent * students.size
            val totalAbsent = allReportData.summaries.sumOf { it.absentHours }
            val overallAttendance = if (totalScheduledAllStudents == 0) 0 else (((totalScheduledAllStudents - totalAbsent).coerceAtLeast(0) * 100.0) / totalScheduledAllStudents).toInt()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ders özeti", fontWeight = FontWeight.SemiBold)
                    Text("Birim: ${config.unit}")
                    Text("Öğrenci sayısı: ${students.size}")
                    Text("Öğrenci başına planlanan saat: ${allReportData.expectedHoursPerStudent}")
                    Text("Toplam devamsız saat: $totalAbsent")
                    Text("Toplam devam oranı: %$overallAttendance")
                    Text("Hesaplama, tanımlı haftalık saatlerin 15 haftada aynı şekilde sürdüğünü varsayar.", style = MaterialTheme.typography.bodySmall)
                }
            }
            ReportExportButtons(
                onPdf = { onExport(null, ReportFormat.PDF) },
                onExcel = { onExport(null, ReportFormat.EXCEL) },
                enabled = students.isNotEmpty()
            )
            Spacer(Modifier.height(5.dp))
            Text("Öğrenci listesi · ayrıntı için öğrenciye dokunun", fontWeight = FontWeight.SemiBold)
            if (rows.isEmpty()) {
                EmptyState("Öğrenci listesi yok. Ders Ekle bölümünden Excel veya CSV aktarın.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                    items(rows, key = { it.student.studentNo }) { row ->
                        Card(
                            Modifier.fillMaxWidth().clickable { onSelectStudent(row.student.studentNo) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(row.student.studentNo, fontWeight = FontWeight.SemiBold)
                                    Text(row.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }, style = MaterialTheme.typography.bodyMedium)
                                    Text("Devamsız: ${row.absentHours} / ${row.scheduledHours} saat", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("%${row.attendancePercent}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportExportButtons(
    onPdf: () -> Unit,
    onExcel: () -> Unit,
    enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPdf, modifier = Modifier.weight(1f), enabled = enabled) {
            Text("PDF dışa aktar")
        }
        Button(onClick = onExcel, modifier = Modifier.weight(1f), enabled = enabled) {
            Text("Excel (.xlsx)")
        }
    }
}

@Composable
private fun StudentSummaryCard(summary: StudentSummary) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(summary.student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Öğrenci No: ${summary.student.studentNo}")
            Divider()
            Text("Planlanan ders saati: ${summary.scheduledHours}")
            Text("Devamsız saat: ${summary.absentHours}", color = MaterialTheme.colorScheme.error)
            Text("Devam edilen saat: ${summary.attendedHours}")
            Text("Devam oranı: %${summary.attendancePercent}", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun loadReportData(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?
): ReportData {
    val start = config.startDate
    val end = config.startDate.plusWeeks(14).plusDays(6)
    val absences = db.getAbsences(courseId = config.id, studentNo = studentNo)
        .filter { it.date >= start && it.date <= end }
    val reportStudents = students.filter { studentNo == null || it.studentNo == studentNo }
    val expected = config.hoursByDay.values.sum() * 15
    val summaries = reportStudents.map { student ->
        StudentSummary(student, expected, absences.count { it.studentNo == student.studentNo })
    }
    return ReportData(summaries, absences, expected)
}

private fun buildReportPdf(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?
): ByteArray {
    val data = loadReportData(config, students, db, studentNo)
    val writer = PdfTextWriter()
    writer.line("DEVAM TAKİP RAPORU", bold = true, size = 17f, spacing = 28f)
    writer.line("Ders: ${config.courseName}", bold = true, size = 12f, spacing = 19f)
    writer.line("Birim: ${config.unit}")
    writer.line("Rapor tarihi: ${LocalDate.now().format(dateFormatter)}")
    writer.line("Dönem: ${config.startDate.format(dateFormatter)} – ${config.startDate.plusWeeks(14).plusDays(6).format(dateFormatter)}")
    writer.blank()
    writer.line("ÖĞRENCİ ÖZETİ", bold = true, size = 12f, spacing = 22f)
    writer.line("Planlanan ders saati (öğrenci başına): ${data.expectedHoursPerStudent}")
    if (data.summaries.isEmpty()) {
        writer.line("Gösterilecek öğrenci kaydı bulunamadı.")
    } else {
        data.summaries.forEach { summary ->
            writer.line("${summary.student.studentNo} — ${summary.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }}", bold = true)
            writer.line("Planlanan: ${summary.scheduledHours} saat | Devamsız: ${summary.absentHours} saat | Devam: ${summary.attendedHours} saat | Oran: %${summary.attendancePercent}")
            writer.blank(3f)
        }
    }
    writer.blank(6f)
    writer.line("DEVAMSIZLIK AYRINTILARI", bold = true, size = 12f, spacing = 22f)
    if (data.absences.isEmpty()) {
        writer.line("Bu rapor kapsamındaki devamsızlık kaydı bulunmuyor.")
    } else {
        data.absences.forEach { absence ->
            val student = students.firstOrNull { it.studentNo == absence.studentNo }
            val name = student?.fullName.orEmpty().ifBlank { "Adı Soyadı bilgisi bulunamadı" }
            writer.line("${absence.date.format(dateFormatter)} · ${dayNames[absence.date.dayOfWeek.value] ?: ""} · ${absence.hour}. ders saati")
            writer.line("${absence.studentNo} — $name")
            writer.blank(3f)
        }
    }
    return writer.finish()
}

private fun buildReportXlsx(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?
): ByteArray {
    val data = loadReportData(config, students, db, studentNo)
    val summaryRows = mutableListOf<List<String>>()
    summaryRows += listOf("Ders", "Birim", "Öğrenci No", "Adı Soyadı", "Planlanan Saat", "Devamsız Saat", "Devam Edilen Saat", "Devam Oranı (%)")
    data.summaries.forEach { summary ->
        summaryRows += listOf(
            config.courseName, config.unit, summary.student.studentNo,
            summary.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" },
            summary.scheduledHours.toString(), summary.absentHours.toString(),
            summary.attendedHours.toString(), summary.attendancePercent.toString()
        )
    }
    val detailRows = mutableListOf<List<String>>()
    detailRows += listOf("Ders", "Birim", "Öğrenci No", "Adı Soyadı", "Tarih", "Gün", "Ders Saati")
    data.absences.forEach { absence ->
        val student = students.firstOrNull { it.studentNo == absence.studentNo }
        detailRows += listOf(
            config.courseName, config.unit, absence.studentNo,
            student?.fullName.orEmpty().ifBlank { "Adı Soyadı bilgisi bulunamadı" },
            absence.date.format(dateFormatter), dayNames[absence.date.dayOfWeek.value] ?: "", absence.hour.toString()
        )
    }

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.addTextEntry("[Content_Types].xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent())
        zip.addTextEntry("_rels/.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent())
        zip.addTextEntry("xl/workbook.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Özet" sheetId="1" r:id="rId1"/>
                <sheet name="Devamsızlık Ayrıntıları" sheetId="2" r:id="rId2"/>
              </sheets>
            </workbook>
        """.trimIndent())
        zip.addTextEntry("xl/_rels/workbook.xml.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
            </Relationships>
        """.trimIndent())
        zip.addTextEntry("xl/worksheets/sheet1.xml", buildWorksheetXml(summaryRows))
        zip.addTextEntry("xl/worksheets/sheet2.xml", buildWorksheetXml(detailRows))
    }
    return output.toByteArray()
}

private fun ZipOutputStream.addTextEntry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun buildWorksheetXml(rows: List<List<String>>): String {
    val xml = StringBuilder()
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
    rows.forEachIndexed { rowIndex, row ->
        val rowNumber = rowIndex + 1
        xml.append("<row r=\"").append(rowNumber).append("\">")
        row.forEachIndexed { columnIndex, value ->
            val cellReference = excelColumnName(columnIndex + 1) + rowNumber
            xml.append("<c r=\"").append(cellReference).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
            xml.append(xmlEscape(value))
            xml.append("</t></is></c>")
        }
        xml.append("</row>")
    }
    xml.append("</sheetData></worksheet>")
    return xml.toString()
}

private fun excelColumnName(number: Int): String {
    var value = number
    val result = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        result.insert(0, ('A'.code + remainder).toChar())
        value = (value - 1) / 26
    }
    return result.toString()
}

private fun xmlEscape(value: String): String = value
    .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private class PdfTextWriter {
    private val document = PdfDocument()
    private val pageWidth = 595
    private val pageHeight = 842
    private val left = 38f
    private val contentWidth = 519f
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        textSize = 8f
        typeface = Typeface.DEFAULT
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
    }
    private var pageNumber = 1
    private var page: PdfDocument.Page? = null
    private var canvas: android.graphics.Canvas? = null
    private var y = 44f

    init { startPage() }

    private fun startPage() {
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page?.canvas
        y = 42f
        if (pageNumber > 1) {
            canvas?.drawText("Devam Takip Raporu · devam", left, y, footerPaint)
            y += 22f
        }
    }

    private fun finishPage() {
        val currentCanvas = canvas ?: return
        currentCanvas.drawText("Sayfa $pageNumber", pageWidth - 72f, pageHeight - 20f, footerPaint)
        page?.let { document.finishPage(it) }
        page = null
        canvas = null
        pageNumber++
    }

    private fun ensureSpace(lineSpacing: Float) {
        if (y + lineSpacing > pageHeight - 42f) {
            finishPage()
            startPage()
        }
    }

    fun line(text: String, bold: Boolean = false, size: Float = 10f, spacing: Float = size + 7f) {
        val paint = (if (bold) boldPaint else bodyPaint).apply { textSize = size }
        if (text.isEmpty()) {
            ensureSpace(spacing)
            y += spacing
            return
        }
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, contentWidth, null).coerceAtLeast(1)
            val chunk = remaining.substring(0, count).trimEnd()
            ensureSpace(spacing)
            canvas?.drawText(chunk, left, y, paint)
            y += spacing
            remaining = remaining.substring(count).trimStart()
        }
    }

    fun blank(spacing: Float = 10f) {
        ensureSpace(spacing)
        y += spacing
    }

    fun finish(): ByteArray {
        finishPage()
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}

private fun reportFilename(config: AppConfig, studentNo: String?, extension: String): String {
    val base = if (studentNo == null) {
        "${safeFilename(config.courseName)}_Toplu_Devam_Raporu"
    } else {
        "${safeFilename(config.courseName)}_${safeFilename(studentNo)}_Raporu"
    }
    return "$base.$extension"
}

private fun safeFilename(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "ogrenci" }

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index) ?: "students.xlsx"
        }
    }
    return "students.xlsx"
}

private fun parseStudentFile(filename: String, input: InputStream): List<Student> {
    val rows = when {
        filename.endsWith(".xlsx", ignoreCase = true) -> parseXlsxRows(input)
        filename.endsWith(".csv", ignoreCase = true) -> parseCsvRows(input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        filename.endsWith(".xls", ignoreCase = true) -> error("Eski .xls biçimi desteklenmiyor. Dosyayı Excel'de .xlsx veya .csv olarak kaydedip yeniden seçin.")
        else -> error("Desteklenen dosyalar .xlsx ve .csv biçimleridir.")
    }
    if (rows.isEmpty()) return emptyList()

    data class Header(val row: Int, val no: Int, val combined: Int, val first: Int, val last: Int)
    fun detectHeader(row: List<String>, index: Int): Header? {
        val headers = row.map { normalizeHeader(it) }
        val noIndex = headers.indexOfFirst {
            it.contains("ogrenci no") || it.contains("ogrenci numara") || it == "no" ||
                it.contains("numara") || it == "student id" || it == "student no" || it == "student number"
        }
        val combinedIndex = headers.indexOfFirst {
            it == "adi soyadi" || it == "ad soyad" || it == "ogrenci adi soyadi" ||
                it == "ogrenci ad soyad" || it == "ad soyadi" || it == "ogrenci adi ve soyadi"
        }
        val firstIndex = headers.indexOfFirst {
            it == "ad" || it == "adi" || it == "isim" || it == "name" || it == "first name" ||
                it == "ogrenci adi" || it == "first_name"
        }
        val lastIndex = headers.indexOfFirst {
            it == "soyad" || it == "soyadi" || it == "surname" || it == "last name" || it == "last_name"
        }
        return if (noIndex >= 0 && (combinedIndex >= 0 || (firstIndex >= 0 && lastIndex >= 0))) {
            Header(index, noIndex, combinedIndex, firstIndex, lastIndex)
        } else null
    }

    val foundHeader = rows.take(25).mapIndexedNotNull { index, row -> detectHeader(row, index) }.firstOrNull()
    val result = linkedMapOf<String, Student>()
    if (foundHeader != null) {
        rows.drop(foundHeader.row + 1).forEach { row ->
            val number = row.getOrNull(foundHeader.no)?.trim().orEmpty()
            if (number.isBlank()) return@forEach
            if (foundHeader.combined >= 0) {
                // Keep the combined value intact so multi-part first names and surnames display exactly as in Excel.
                val fullName = row.getOrNull(foundHeader.combined)?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                if (fullName.isNotBlank()) result[number] = Student(number, fullName, "")
            } else {
                val firstName = row.getOrNull(foundHeader.first)?.trim().orEmpty()
                val lastName = row.getOrNull(foundHeader.last)?.trim().orEmpty()
                if (firstName.isNotBlank() || lastName.isNotBlank()) result[number] = Student(number, firstName, lastName)
            }
        }
    } else {
        // Headerless files: column A = student number, B = full name, or B/C = first/last name.
        rows.forEach { row ->
            val number = row.getOrNull(0)?.trim().orEmpty()
            val first = row.getOrNull(1)?.trim().orEmpty()
            val last = row.getOrNull(2)?.trim().orEmpty()
            if (number.isBlank() || (first.isBlank() && last.isBlank())) return@forEach
            // Skip a probable header row even when the sheet has unusual column labels.
            if (normalizeHeader(number).contains("ogrenci") && normalizeHeader(first).contains("ad")) return@forEach
            result[number] = Student(number, first, last)
        }
    }
    return result.values.toList()
}

private fun normalizeHeader(input: String): String {
    val lower = input.trim().lowercase(Locale("tr", "TR"))
    val mapped = lower
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
    return mapped.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

private fun parseXlsxRows(input: InputStream): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
    val workbookBytes = entries["xl/workbook.xml"] ?: error("Excel çalışma kitabı okunamadı.")
    val workbookDoc = parseXml(workbookBytes)
    val sheetNode = workbookDoc.getElementsByTagName("sheet").item(0) as? Element
        ?: error("Excel dosyasında sayfa bulunamadı.")
    val relId = sheetNode.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        .ifBlank { sheetNode.getAttribute("r:id") }
    val relsBytes = entries["xl/_rels/workbook.xml.rels"] ?: error("Excel sayfa bilgileri okunamadı.")
    val relsDoc = parseXml(relsBytes)
    var target = "worksheets/sheet1.xml"
    val relationships = relsDoc.getElementsByTagName("Relationship")
    for (i in 0 until relationships.length) {
        val relation = relationships.item(i) as? Element ?: continue
        if (relation.getAttribute("Id") == relId) {
            target = relation.getAttribute("Target").ifBlank { target }
            break
        }
    }
    val sheetPath = when {
        target.startsWith("/") -> target.removePrefix("/")
        target.startsWith("xl/") -> target
        else -> "xl/$target"
    }
    val sheetBytes = entries[sheetPath] ?: error("İlk çalışma sayfası bulunamadı ($sheetPath).")
    val sheetDoc = parseXml(sheetBytes)
    val rowNodes = sheetDoc.getElementsByTagName("row")
    val result = mutableListOf<List<String>>()
    for (r in 0 until rowNodes.length) {
        val rowElement = rowNodes.item(r) as? Element ?: continue
        val cells = rowElement.getElementsByTagName("c")
        val rowMap = sortedMapOf<Int, String>()
        var maxColumn = -1
        for (c in 0 until cells.length) {
            val cell = cells.item(c) as? Element ?: continue
            val index = cellReferenceToIndex(cell.getAttribute("r"))
            if (index < 0) continue
            val type = cell.getAttribute("t")
            val value = when (type) {
                "s" -> {
                    val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                    sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                }
                "inlineStr" -> cell.getElementsByTagName("is").item(0)?.textContent.orEmpty()
                "b" -> if (cell.getElementsByTagName("v").item(0)?.textContent == "1") "TRUE" else "FALSE"
                else -> {
                    val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                    if (raw.matches(Regex("-?\\d+\\.0+"))) runCatching { BigDecimal(raw).toBigInteger().toString() }.getOrDefault(raw) else raw
                }
            }
            rowMap[index] = value.trim()
            maxColumn = maxOf(maxColumn, index)
        }
        if (maxColumn >= 0) result += (0..maxColumn).map { rowMap[it].orEmpty() }
    }
    return result
}

private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val doc = parseXml(bytes)
    val nodes = doc.getElementsByTagName("si")
    return (0 until nodes.length).map { nodes.item(it).textContent.orEmpty() }
}

private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
}.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

private fun cellReferenceToIndex(reference: String): Int {
    val letters = reference.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
    if (letters.isEmpty()) return -1
    var number = 0
    letters.forEach { ch -> number = number * 26 + (ch - 'A' + 1) }
    return number - 1
}

private fun parseCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            (ch == ',' || ch == ';' || ch == '\t') && !quoted -> { row += field.toString(); field.setLength(0) }
            (ch == '\n' || ch == '\r') && !quoted -> {
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row += field.toString()
                if (row.any { it.isNotBlank() }) rows += row.toList()
                row.clear(); field.setLength(0)
            }
            else -> field.append(ch)
        }
        i++
    }
    row += field.toString()
    if (row.any { it.isNotBlank() }) rows += row.toList()
    return rows
}
