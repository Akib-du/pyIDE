package com.example.pyide

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        val py = Python.getInstance().getModule("runner")
        py.callAttr(
            "init",
            File(filesDir, "site").absolutePath,
            File(cacheDir, "plots").absolutePath,
            File(cacheDir, "mpl").absolutePath
        )
        val proj = File(filesDir, "projects").apply { mkdirs() }
        if (proj.listFiles().isNullOrEmpty()) {
            assets.list("samples")?.forEach { n ->
                assets.open("samples/$n").use { i -> File(proj, n).outputStream().use { o -> i.copyTo(o) } }
            }
        }
        setContent { App(py, proj) }
    }
}

@Composable
fun App(py: PyObject, proj: File) {
    var dark by remember { mutableStateOf(true) }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) { Ide(py, proj, dark) { dark = !dark } }
    }
}

data class Problem(val line: Int, val col: Int, val msg: String)

// ---------- Syntax highlighting ----------
val KEYWORDS = setOf(
    "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try",
    "except", "finally", "with", "lambda", "pass", "break", "continue", "in", "is", "not", "and",
    "or", "None", "True", "False", "yield", "raise", "global", "nonlocal", "async", "await", "del", "assert"
)
val TOKEN = Regex(
    "(#.*)|(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
        "|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b[A-Za-z_]\\w*\\b)"
)

class PyHighlighter(dark: Boolean) : VisualTransformation {
    private val cComment = if (dark) Color(0xFF7A7E85) else Color(0xFF8C8C8C)
    private val cString = if (dark) Color(0xFF6AAB73) else Color(0xFF067D17)
    private val cNumber = if (dark) Color(0xFF2AACB8) else Color(0xFF1750EB)
    private val cKeyword = if (dark) Color(0xFFCF8E6D) else Color(0xFF0033B3)

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val styled = buildAnnotatedString {
            append(src)
            TOKEN.findAll(src).forEach { m ->
                val c = when {
                    m.groups[1] != null -> cComment
                    m.groups[2] != null -> cString
                    m.groups[3] != null -> cNumber
                    m.value in KEYWORDS -> cKeyword
                    else -> null
                } ?: return@forEach
                addStyle(SpanStyle(color = c), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

// ---------- Helpers ----------
fun lineCol(v: TextFieldValue): Pair<Int, Int> {
    val before = v.text.substring(0, v.selection.start)
    return (before.count { it == '\n' } + 1) to (before.length - (before.lastIndexOf('\n') + 1))
}

fun applyAutoIndent(old: TextFieldValue, n: TextFieldValue): TextFieldValue {
    val s = n.selection.start
    if (n.text.length == old.text.length + 1 && n.selection.collapsed && s > 0 && n.text[s - 1] == '\n') {
        val prev = n.text.substring(0, s - 1).substringAfterLast('\n')
        var indent = prev.takeWhile { it == ' ' }
        if (prev.trimEnd().endsWith(":")) indent += "    "
        return TextFieldValue(n.text.substring(0, s) + indent + n.text.substring(s), TextRange(s + indent.length))
    }
    return n
}

// ---------- UI ----------
@Composable
fun Ide(py: PyObject, proj: File, dark: Boolean, toggleTheme: () -> Unit) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(proj.listFiles()!!.map { it.name }.sorted()) }
    var current by remember { mutableStateOf(files.first()) }
    var tfv by remember { mutableStateOf(TextFieldValue(File(proj, current).readText())) }
    var tab by remember { mutableStateOf(0) }  // 0 Console, 1 Problems, 2 Plots, 3 Packages
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var waitingInput by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var consoleInput by remember { mutableStateOf("") }
    var consoleContinuation by remember { mutableStateOf(false) }
    var consoleBusy by remember { mutableStateOf(false) }
    var consoleFraction by remember { mutableStateOf(0.32f) }
    var plots by remember { mutableStateOf(listOf<String>()) }
    var problems by remember { mutableStateOf(listOf<Problem>()) }
    var completions by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var pkgName by remember { mutableStateOf("") }
    var pkgLog by remember { mutableStateOf("") }
    var pipBusy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val bridge = remember {
        Bridge(
            onOut = { s -> output = (output + s).takeLast(200_000) },
            onPlotCb = { p -> if (plots.isEmpty()) tab = 2; plots = plots + p },
            onInput = { w -> waitingInput = w; if (w) tab = 0 }
        )
    }

    fun open(name: String) {
        current = name
        tfv = TextFieldValue(File(proj, name).readText())
        menu = false
    }

    fun onEdit(n: TextFieldValue) {
        val v = applyAutoIndent(tfv, n)
        tfv = v
        File(proj, current).writeText(v.text)
    }

    fun goTo(line: Int) {
        var off = 0
        repeat(line - 1) { val i = tfv.text.indexOf('\n', off); if (i >= 0) off = i + 1 }
        tfv = tfv.copy(selection = TextRange(off))
    }

    fun sendInput() {
        val l = inputText
        inputText = ""
        waitingInput = false
        output += l + "\n"
        bridge.submit(l)
    }

    fun sendConsole() {
        val command = consoleInput
        if (command.isBlank() && !consoleContinuation) return
        consoleInput = ""
        output += (if (consoleContinuation) "... " else ">>> ") + command + "\n"
        scope.launch {
            consoleBusy = true
            try {
                val complete = withContext(Dispatchers.IO) {
                    py.callAttr("console", command, proj.absolutePath, bridge).toString() == "True"
                }
                consoleContinuation = !complete
            } catch (e: Exception) {
                output += "Console error: ${e.message}\n"
                consoleContinuation = false
            } finally {
                consoleBusy = false
            }
        }
    }

    fun run() = scope.launch {
        running = true; tab = 0; output = ""; plots = emptyList(); waitingInput = false; consoleContinuation = false
        withContext(Dispatchers.IO) { py.callAttr("run", tfv.text, proj.absolutePath, bridge) }
        running = false; waitingInput = false
    }

    fun stop() {
        bridge.cancel()
        scope.launch(Dispatchers.IO) { py.callAttr("stop") }
    }

    // Error list (pyflakes)
    LaunchedEffect(tfv.text) {
        delay(500)
        problems = withContext(Dispatchers.IO) {
            val arr = JSONArray(py.callAttr("lint", tfv.text).toString())
            (0 until arr.length()).map { val a = arr.getJSONArray(it); Problem(a.getInt(0), a.getInt(1), a.getString(2)) }
        }
    }

    // Autocomplete (jedi)
    LaunchedEffect(tfv.text, tfv.selection) {
        delay(250)
        val s = tfv.selection.start
        val prevOk = s > 0 && tfv.selection.collapsed && tfv.text[s - 1].let { it.isLetterOrDigit() || it == '_' || it == '.' }
        completions = if (!prevOk) emptyList() else withContext(Dispatchers.IO) {
            val (l, c) = lineCol(tfv)
            val arr = JSONArray(py.callAttr("complete", tfv.text, l, c).toString())
            (0 until arr.length()).map { arr.getJSONArray(it).let { a -> a.getString(0) to a.getString(1) } }
        }
    }

    // Bundled library list
    LaunchedEffect(tab) {
        if (tab == 3 && pkgLog.isEmpty())
            pkgLog = withContext(Dispatchers.IO) { py.callAttr("bundled").toString() }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { menu = true }) { Text(current + " ▾") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    files.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { open(f) }) }
                    DropdownMenuItem(text = { Text("+ New file") }, onClick = { menu = false; showNew = true })
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { File(proj, current).writeText(tfv.text) }) { Text("Save") }
            TextButton(onClick = toggleTheme) { Text(if (dark) "Light" else "Dark") }
            if (running) Button(onClick = { stop() }) { Text("■ Stop") }
            else Button(onClick = { run() }) { Text("▶ Run") }
        }

        // Editor with line numbers
        Box(Modifier.weight(1f - consoleFraction).fillMaxWidth()) {
            val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
            val fg = MaterialTheme.colorScheme.onSurface
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    (1..(tfv.text.count { it == '\n' } + 1)).joinToString("\n"),
                    style = mono.copy(color = Color.Gray, textAlign = TextAlign.End),
                    modifier = Modifier.width(44.dp).padding(top = 8.dp, end = 8.dp)
                )
                BasicTextField(
                    value = tfv,
                    onValueChange = { onEdit(it) },
                    textStyle = mono.copy(color = fg),
                    cursorBrush = SolidColor(fg),
                    visualTransformation = remember(dark) { PyHighlighter(dark) },
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(top = 8.dp)
                )
            }
        }

        // Autocomplete suggestions
        if (completions.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(completions) { (name, suffix) ->
                    AssistChip(onClick = {
                        val s = tfv.selection.start
                        val t = tfv.text.substring(0, s) + suffix + tfv.text.substring(s)
                        onEdit(TextFieldValue(t, TextRange(s + suffix.length)))
                    }, label = { Text(name, fontFamily = FontFamily.Monospace) })
                }
            }
        }

        // Draggable divider: adjust the editor / console split.
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        consoleFraction = (consoleFraction - dragAmount / 900f).coerceIn(0.18f, 0.75f)
                    }
                }
        )

        // Bottom panel
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Console") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Problems (${problems.size})") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Plots (${plots.size})") })
            Tab(tab == 3, { tab = 3 }, text = { Text("Packages") })
        }
        Box(Modifier.weight(consoleFraction).fillMaxWidth().padding(8.dp)) {
            val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            when (tab) {
                0 -> Column(Modifier.fillMaxSize()) {
                    val sc = rememberScrollState()
                    LaunchedEffect(output.length) { sc.scrollTo(sc.maxValue) }
                    Text(output, style = mono, modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(sc))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = if (waitingInput) inputText else consoleInput,
                            onValueChange = { if (waitingInput) inputText = it else consoleInput = it },
                            singleLine = true,
                            label = { Text(if (waitingInput) "Program input" else if (consoleContinuation) "... continue Python command" else "Python command") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Ascii),
                            keyboardActions = KeyboardActions(onSend = { if (waitingInput) sendInput() else sendConsole() }),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = waitingInput || (!running && !consoleBusy),
                            onClick = { if (waitingInput) sendInput() else sendConsole() }
                        ) { Text(if (waitingInput) "Send" else "Enter") }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (problems.isEmpty()) Text("No problems found ✓", style = mono)
                    problems.forEach { p ->
                        Text("Line ${p.line}: ${p.msg}", style = mono, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().clickable { goTo(p.line) }.padding(vertical = 4.dp))
                    }
                }
                2 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (plots.isEmpty()) Text("No plots yet. Call plt.show() in your code.", style = mono)
                    plots.forEach { p ->
                        val bmp = remember(p) { BitmapFactory.decodeFile(p)?.asImageBitmap() }
                        if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth().padding(bottom = 8.dp), contentScale = ContentScale.FillWidth)
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(pkgName, { pkgName = it }, label = { Text("Pure-Python package (PyPI)") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = pkgName.isNotBlank() && !pipBusy, onClick = {
                            scope.launch {
                                pipBusy = true; pkgLog = "Installing ${pkgName.trim()}…"
                                pkgLog = withContext(Dispatchers.IO) {
                                    try { py.callAttr("pip_install", pkgName.trim()).toString() } catch (e: Exception) { "Error: ${e.message}" }
                                }
                                pipBusy = false
                            }
                        }) { Text("Install") }
                    }
                    Text(pkgLog, style = mono, modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp))
                }
            }
        }
    }

    if (showNew) AlertDialog(
        onDismissRequest = { showNew = false },
        title = { Text("New file") },
        text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("name.py") }) },
        confirmButton = {
            TextButton(onClick = {
                val n = newName.trim().let { if (it.endsWith(".py")) it else "$it.py" }
                if (newName.isNotBlank()) {
                    File(proj, n).apply { if (!exists()) writeText("") }
                    files = proj.listFiles()!!.map { it.name }.sorted()
                    open(n)
                }
                newName = ""; showNew = false
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
    )
}
