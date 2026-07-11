package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.max

sealed class VisualGate {
    abstract val column: Int
    data class Single(val name: String, val qubit: Int, override val column: Int, val param: String? = null) : VisualGate()
    data class Controlled(val name: String, val control: Int, val target: Int, override val column: Int) : VisualGate()
    data class Measure(val qubit: Int, override val column: Int) : VisualGate()
}

data class ParsedCircuit(
    val numQubits: Int,
    val gates: List<VisualGate>,
    val maxColumns: Int
)

object QasmParser {
    fun parse(qasm: String): ParsedCircuit {
        val lines = qasm.split("\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        var numQubits = 3
        val gates = mutableListOf<VisualGate>()

        // Scan declarations
        for (line in lines) {
            if (line.startsWith("qreg q[") || line.startsWith("qubit[")) {
                val start = line.indexOf('[')
                val end = line.indexOf(']')
                if (start != -1 && end != -1) {
                    val qCount = line.substring(start + 1, end).toIntOrNull()
                    if (qCount != null) {
                        numQubits = qCount
                    }
                }
            }
        }

        val nextAvailableCol = IntArray(30) { 0 }

        for (line in lines) {
            if (line.startsWith("OPENQASM") || line.startsWith("include") || line.startsWith("qreg") || line.startsWith("creg") || line.startsWith("//") || line.startsWith("creg")) {
                continue
            }

            // Simple cleaning of inline comments
            val cleanLine = if (line.contains("//")) line.split("//")[0].trim() else line
            if (cleanLine.isEmpty()) continue

            val parts = cleanLine.split(Regex("\\s+"), 2)
            if (parts.isEmpty()) continue

            val gateNameWithParam = parts[0].lowercase()
            val rest = parts.getOrNull(1) ?: ""

            val args = rest.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            if (gateNameWithParam.startsWith("measure")) {
                if (rest.contains("->")) {
                    val leftOfArrow = rest.split("->")[0].trim()
                    if (leftOfArrow == "q") {
                        val col = (0 until numQubits).map { nextAvailableCol[it] }.maxOrNull() ?: 0
                        for (q in 0 until numQubits) {
                            gates.add(VisualGate.Measure(q, col))
                            nextAvailableCol[q] = col + 1
                        }
                    } else {
                        val qubitIndex = extractQubitIndex(leftOfArrow)
                        if (qubitIndex != null && qubitIndex < nextAvailableCol.size) {
                            val col = nextAvailableCol[qubitIndex]
                            gates.add(VisualGate.Measure(qubitIndex, col))
                            nextAvailableCol[qubitIndex] = col + 1
                        }
                    }
                }
                continue
            }

            val gateName = if (gateNameWithParam.contains("(")) {
                gateNameWithParam.substring(0, gateNameWithParam.indexOf("("))
            } else {
                gateNameWithParam
            }
            val param = if (gateNameWithParam.contains("(")) {
                val start = gateNameWithParam.indexOf("(")
                val end = gateNameWithParam.indexOf(")")
                if (start != -1 && end != -1) gateNameWithParam.substring(start + 1, end) else null
            } else {
                null
            }

            if (gateName == "cx" || gateName == "cnot") {
                if (args.size >= 2) {
                    val ctrl = extractQubitIndex(args[0])
                    val tgt = extractQubitIndex(args[1])
                    if (ctrl != null && tgt != null && ctrl < nextAvailableCol.size && tgt < nextAvailableCol.size) {
                        val col = max(nextAvailableCol[ctrl], nextAvailableCol[tgt])
                        gates.add(VisualGate.Controlled(gateName.uppercase(), ctrl, tgt, col))
                        nextAvailableCol[ctrl] = col + 1
                        nextAvailableCol[tgt] = col + 1
                    }
                }
            } else if (gateName.isNotEmpty() && args.isNotEmpty()) {
                val qubitIndex = extractQubitIndex(args[0])
                if (qubitIndex != null && qubitIndex < nextAvailableCol.size) {
                    val col = nextAvailableCol[qubitIndex]
                    gates.add(VisualGate.Single(gateName.uppercase(), qubitIndex, col, param))
                    nextAvailableCol[qubitIndex] = col + 1
                }
            }
        }

        var determinedQubits = numQubits
        for (gate in gates) {
            val maxQ = when (gate) {
                is VisualGate.Single -> gate.qubit
                is VisualGate.Controlled -> max(gate.control, gate.target)
                is VisualGate.Measure -> gate.qubit
            }
            if (maxQ + 1 > determinedQubits) {
                determinedQubits = maxQ + 1
            }
        }

        val maxCols = (nextAvailableCol.maxOrNull() ?: 0).coerceAtLeast(1)

        return ParsedCircuit(
            numQubits = determinedQubits.coerceIn(1, 10),
            gates = gates,
            maxColumns = maxCols
        )
    }

    private fun extractQubitIndex(str: String): Int? {
        val regex = Regex("q\\[(\\d+)\\]")
        val match = regex.find(str)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        return str.filter { it.isDigit() }.toIntOrNull()
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun QuantumCircuitVisualizer(
    qasmCode: String,
    modifier: Modifier = Modifier
) {
    val circuit = remember(qasmCode) { QasmParser.parse(qasmCode) }
    val textMeasurer = rememberTextMeasurer()

    val wireSpacing = 48.dp
    val columnWidth = 56.dp
    val leftPadding = 50.dp
    val rightPadding = 40.dp
    val paddingTop = 28.dp
    val classicalRegisterSpacing = 36.dp

    val density = androidx.compose.ui.platform.LocalDensity.current

    val wireSpacingPx = with(density) { wireSpacing.toPx() }
    val columnWidthPx = with(density) { columnWidth.toPx() }
    val leftPaddingPx = with(density) { leftPadding.toPx() }
    val paddingTopPx = with(density) { paddingTop.toPx() }
    val classicalRegisterSpacingPx = with(density) { classicalRegisterSpacing.toPx() }

    val calculatedHeight = paddingTop + (wireSpacing * (circuit.numQubits - 1)) + classicalRegisterSpacing + 20.dp
    val calculatedWidth = leftPadding + (columnWidth * circuit.maxColumns) + rightPadding

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CyberSurface)
            .border(0.5.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(CyberGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🧬 Q-CORE ACTIVE CIRCUIT INTERFACE (NATIVE)",
                        color = CyberCyan,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${circuit.numQubits} QUBITS | ${circuit.maxColumns} STAGES",
                    color = CyberTealMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(calculatedWidth)
                        .height(calculatedHeight)
                ) {
                    val classicalY = paddingTopPx + (circuit.numQubits - 1) * wireSpacingPx + classicalRegisterSpacingPx

                    // 1. Draw horizontal wires (qubits)
                    for (i in 0 until circuit.numQubits) {
                        val y = paddingTopPx + i * wireSpacingPx
                        
                        // Draw wire line
                        drawLine(
                            color = CyberGray.copy(alpha = 0.6f),
                            start = Offset(leftPaddingPx - 10f, y),
                            end = Offset(size.width - 10f, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Draw label "q[i]"
                        drawText(
                            textMeasurer = textMeasurer,
                            text = AnnotatedString("q[$i]"),
                            topLeft = Offset(10f, y - 10.dp.toPx()),
                            style = TextStyle(
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // 2. Draw classical register wire (double lines)
                    drawLine(
                        color = CyberTealMuted.copy(alpha = 0.4f),
                        start = Offset(leftPaddingPx - 10f, classicalY - 2.dp.toPx()),
                        end = Offset(size.width - 10f, classicalY - 2.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = CyberTealMuted.copy(alpha = 0.4f),
                        start = Offset(leftPaddingPx - 10f, classicalY + 2.dp.toPx()),
                        end = Offset(size.width - 10f, classicalY + 2.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Draw classical label "c"
                    drawText(
                        textMeasurer = textMeasurer,
                        text = AnnotatedString("c"),
                        topLeft = Offset(10f, classicalY - 10.dp.toPx()),
                        style = TextStyle(
                            color = CyberTealMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // 3. Draw visual stage separators
                    for (col in 0..circuit.maxColumns) {
                        val x = leftPaddingPx + col * columnWidthPx
                        drawLine(
                            color = CyberCyan.copy(alpha = 0.04f),
                            start = Offset(x, 0f),
                            end = Offset(x, classicalY + 15.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    // 4. Render Gates
                    circuit.gates.forEach { gate ->
                        val colX = leftPaddingPx + gate.column * columnWidthPx + (columnWidthPx / 2f)

                        when (gate) {
                            is VisualGate.Single -> {
                                val y = paddingTopPx + gate.qubit * wireSpacingPx
                                val sizePx = 28.dp.toPx()
                                
                                // Draw gate container
                                drawRoundRect(
                                    color = when (gate.name) {
                                        "H" -> CyberPurple.copy(alpha = 0.85f)
                                        "X" -> CyberRed.copy(alpha = 0.85f)
                                        "Y" -> CyberGold.copy(alpha = 0.85f)
                                        "Z" -> CyberBlue.copy(alpha = 0.85f)
                                        else -> CyberCyan.copy(alpha = 0.85f)
                                    },
                                    topLeft = Offset(colX - (sizePx / 2f), y - (sizePx / 2f)),
                                    size = Size(sizePx, sizePx),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )

                                // Draw gate border
                                drawRoundRect(
                                    color = CyberWhite,
                                    topLeft = Offset(colX - (sizePx / 2f), y - (sizePx / 2f)),
                                    size = Size(sizePx, sizePx),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )

                                // Draw gate name
                                val nameLayout = textMeasurer.measure(
                                    AnnotatedString(gate.name),
                                    style = TextStyle(
                                        color = CyberWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                drawText(
                                    nameLayout,
                                    topLeft = Offset(colX - (nameLayout.size.width / 2f), y - (nameLayout.size.height / 2f))
                                )

                                // Draw parameters if present
                                if (gate.param != null) {
                                    val paramLabel = if (gate.param.length > 5) gate.param.take(5) + ".." else gate.param
                                    val paramLayout = textMeasurer.measure(
                                        AnnotatedString("($paramLabel)"),
                                        style = TextStyle(
                                            color = CyberTealMuted,
                                            fontSize = 7.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    drawText(
                                        paramLayout,
                                        topLeft = Offset(colX - (paramLayout.size.width / 2f), y + (sizePx / 2f) + 2.dp.toPx())
                                    )
                                }
                            }
                            is VisualGate.Controlled -> {
                                val yCtrl = paddingTopPx + gate.control * wireSpacingPx
                                val yTgt = paddingTopPx + gate.target * wireSpacingPx

                                // Vertical connector line
                                drawLine(
                                    color = CyberCyan,
                                    start = Offset(colX, yCtrl),
                                    end = Offset(colX, yTgt),
                                    strokeWidth = 1.5.dp.toPx()
                                )

                                // Control dot
                                drawCircle(
                                    color = CyberCyan,
                                    radius = 5.dp.toPx(),
                                    center = Offset(colX, yCtrl)
                                )

                                // Target CNOT circle
                                val targetRadius = 10.dp.toPx()
                                drawCircle(
                                    color = CyberSurface,
                                    radius = targetRadius,
                                    center = Offset(colX, yTgt)
                                )
                                drawCircle(
                                    color = CyberCyan,
                                    radius = targetRadius,
                                    center = Offset(colX, yTgt),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )

                                // Cross lines in target
                                drawLine(
                                    color = CyberCyan,
                                    start = Offset(colX, yTgt - targetRadius),
                                    end = Offset(colX, yTgt + targetRadius),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawLine(
                                    color = CyberCyan,
                                    start = Offset(colX - targetRadius, yTgt),
                                    end = Offset(colX + targetRadius, yTgt),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                            is VisualGate.Measure -> {
                                val y = paddingTopPx + gate.qubit * wireSpacingPx
                                val sizePx = 26.dp.toPx()

                                // Measurement Box
                                drawRoundRect(
                                    color = CyberGold.copy(alpha = 0.2f),
                                    topLeft = Offset(colX - (sizePx / 2f), y - (sizePx / 2f)),
                                    size = Size(sizePx, sizePx),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                                drawRoundRect(
                                    color = CyberGold,
                                    topLeft = Offset(colX - (sizePx / 2f), y - (sizePx / 2f)),
                                    size = Size(sizePx, sizePx),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                    style = Stroke(width = 1.2.dp.toPx())
                                )

                                // Draw gauge arc
                                drawArc(
                                    color = CyberGold,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(colX - (8.dp.toPx()), y - 4.dp.toPx()),
                                    size = Size(16.dp.toPx(), 16.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )

                                // Draw needle arrow
                                drawLine(
                                    color = CyberGold,
                                    start = Offset(colX, y + 4.dp.toPx()),
                                    end = Offset(colX + 6.dp.toPx(), y - 6.dp.toPx()),
                                    strokeWidth = 1.2.dp.toPx()
                                )

                                // Dashed line to classical register
                                drawLine(
                                    color = CyberGold.copy(alpha = 0.6f),
                                    start = Offset(colX, y + (sizePx / 2f)),
                                    end = Offset(colX, classicalY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveQuantumCircuitWorkspace(
    initialQasm: String = ""
) {
    var qasmInput by remember {
        mutableStateOf(
            initialQasm.ifEmpty {
                """
                OPENQASM 3.0;
                include "stdgates.inc";
                qreg q[3];
                creg c[3];
                h q[0];
                h q[1];
                h q[2];
                rz(1.5708) q[0];
                cnot q[0], q[1];
                cnot q[1], q[2];
                measure q -> c;
                """.trimIndent()
            }
        )
    }

    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        QuantumCircuitVisualizer(qasmInput)

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            border = BorderStroke(0.5.dp, CyberCyan.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "📝 EDIT OPENQASM SOURCE SCHEMA",
                        color = CyberCyan,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(CyberSurface)
                                .clickable {
                                    qasmInput = """
                                    OPENQASM 3.0;
                                    include "stdgates.inc";
                                    qreg q[2];
                                    creg c[2];
                                    h q[0];
                                    cnot q[0], q[1];
                                    measure q -> c;
                                    """.trimIndent()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("BELL STATE", color = CyberGreen, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(CyberSurface)
                                .clickable {
                                    qasmInput = """
                                    OPENQASM 3.0;
                                    include "stdgates.inc";
                                    qreg q[3];
                                    creg c[3];
                                    h q[0];
                                    cnot q[0], q[1];
                                    cnot q[1], q[2];
                                    measure q -> c;
                                    """.trimIndent()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("GHZ STATE", color = CyberPurple, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = qasmInput,
                    onValueChange = { qasmInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    textStyle = TextStyle(
                        color = CyberWhite,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 13.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CyberSurface,
                        unfocusedContainerColor = CyberSurface,
                        focusedIndicatorColor = CyberCyan,
                        unfocusedIndicatorColor = CyberGray
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Edit QASM schema in real-time to watch circuit update live.",
                        color = CyberTealMuted,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )

                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(qasmInput)) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.12f)),
                        border = BorderStroke(0.5.dp, CyberCyan),
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("⎘ COPY QASM", color = CyberCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
