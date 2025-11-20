package com.example.e_textilesendingserver.ui.visualization

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.e_textilesendingserver.R
import com.example.e_textilesendingserver.core.parser.SensorFrame
import com.example.e_textilesendingserver.data.FramePreviewStore
import com.example.e_textilesendingserver.databinding.ActivityVisualizationBinding
import com.example.e_textilesendingserver.ui.widget.CopBoardView
import com.example.e_textilesendingserver.ui.widget.HeatmapView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class VisualizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisualizationBinding
    private val panelA = PanelState("primary")
    private val panelB = PanelState("secondary")
    private var collectJob: Job? = null
    private var dualMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualizationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupControls()
    }

    override fun onStart() {
        super.onStart()
        FramePreviewStore.setEnabled(true)
        collectJob = lifecycleScope.launch {
            FramePreviewStore.state.collectLatest { map ->
                val devices = map.values.sortedByDescending { it.receivedAt }
                updateDeviceSelectors(devices.map { it.frame.dn })
                updatePanels(devices.associateBy { it.frame.dn })
            }
        }
    }

    override fun onStop() {
        collectJob?.cancel()
        collectJob = null
        FramePreviewStore.setEnabled(false)
        super.onStop()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = getString(R.string.visual_title)
    }

    private fun setupControls() {
        binding.dualSwitch.setOnCheckedChangeListener { _, checked ->
            dualMode = checked
            binding.panelSecondary.root.isVisible = checked
        }
        binding.resetRangeButton.setOnClickListener {
            binding.pressureMin.setText(DEFAULT_MIN.toString())
            binding.pressureMax.setText(DEFAULT_MAX.toString())
        }
        setupPanelUi(panelA, binding.panelPrimary.telemetryHeatmap, binding.panelPrimary.copBoard)
        setupPanelUi(panelB, binding.panelSecondary.telemetryHeatmap, binding.panelSecondary.copBoard)
    }

    private fun setupPanelUi(panel: PanelState, heatmap: HeatmapView, cop: CopBoardView) {
        val selector = if (panel.key == "primary") binding.panelPrimary.deviceSelector else binding.panelSecondary.deviceSelector
        val layoutSelector = if (panel.key == "primary") binding.panelPrimary.layoutSelector else binding.panelSecondary.layoutSelector
        val mirrorRows = if (panel.key == "primary") binding.panelPrimary.mirrorRows else binding.panelSecondary.mirrorRows
        val mirrorCols = if (panel.key == "primary") binding.panelPrimary.mirrorCols else binding.panelSecondary.mirrorCols

        selector.setOnItemClickListener { _, _, position, _ ->
            panel.selectedDn = selector.adapter?.getItem(position) as? String
            panel.selectedLayout = null
        }
        selector.setOnClickListener { selector.showDropDown() }
        selector.threshold = 0

        layoutSelector.setOnItemClickListener { _, _, position, _ ->
            panel.selectedLayout = layoutSelector.adapter?.getItem(position) as? String
        }
        layoutSelector.setOnClickListener { layoutSelector.showDropDown() }
        layoutSelector.threshold = 0
        mirrorRows.setOnCheckedChangeListener { _, checked ->
            panel.mirrorRows = checked
        }
        mirrorCols.setOnCheckedChangeListener { _, checked ->
            panel.mirrorCols = checked
        }
        panel.onRender = { data ->
            val minVal = binding.pressureMin.text.toString().toFloatOrNull() ?: DEFAULT_MIN
            val maxVal = binding.pressureMax.text.toString().toFloatOrNull() ?: DEFAULT_MAX
            heatmap.setData(data.rows, data.cols, data.values, minVal, maxVal)
            cop.setCop(data.copX, data.copY, data.hasCop)
            renderVectors(panel, data.frame)
        }
    }

    private fun updateDeviceSelectors(dns: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, dns)
        binding.panelPrimary.deviceSelector.setAdapter(adapter)
        binding.panelSecondary.deviceSelector.setAdapter(adapter)
        if (dns.isNotEmpty()) {
            binding.panelPrimary.deviceSelector.setText(binding.panelPrimary.deviceSelector.text.takeIf { it.isNotBlank() } ?: dns.first(), false)
            if (dns.size > 1) {
                binding.panelSecondary.deviceSelector.setText(binding.panelSecondary.deviceSelector.text.takeIf { it.isNotBlank() } ?: dns[1], false)
            }
        } else {
            binding.panelPrimary.deviceSelector.setText("", false)
            binding.panelSecondary.deviceSelector.setText("", false)
        }
    }

    private fun updatePanels(map: Map<String, FramePreviewStore.PreviewFrame>) {
        renderPanel(panelA, map, binding.panelPrimary.placeholder)
        if (dualMode) {
            renderPanel(panelB, map, binding.panelSecondary.placeholder)
        }
    }

    private fun renderPanel(state: PanelState, map: Map<String, FramePreviewStore.PreviewFrame>, placeholder: android.view.View) {
        val dn = state.selectedDn ?: map.keys.firstOrNull()
        val entry = dn?.let { map[it] }
        state.selectedDn = dn
        placeholder.isVisible = entry == null
        if (entry == null) return
        val frame = entry.frame
        val pairs = SensorLayoutUtils.computeLayoutOptions(frame.sn)
        val layoutKey = state.selectedLayout ?: pairs.firstOrNull()?.toLayoutKey()
        state.selectedLayout = layoutKey
        val (rows, cols) = layoutKey?.toRowsCols() ?: SensorLayoutUtils.chooseDefaultLayout(frame.sn)
        val values = SensorLayoutUtils.mapPressuresToGrid(
            frame.pressure,
            rows,
            cols,
            state.mirrorRows,
            state.mirrorCols,
        )
        val cop = SensorLayoutUtils.computeCop(
            frame.pressure,
            rows,
            cols,
            state.mirrorRows,
            state.mirrorCols,
        )
        val uiData = PanelRenderData(
            frame = frame,
            rows = rows,
            cols = cols,
            values = values,
            hasCop = cop.hasData,
            copX = cop.normX,
            copY = cop.normY,
        )
        state.onRender?.invoke(uiData)
        updateLayoutSelectorState(state, pairs)
    }

    private fun updateLayoutSelectorState(state: PanelState, options: List<Pair<Int, Int>>) {
        val layoutSelector = if (state.key == "primary") binding.panelPrimary.layoutSelector else binding.panelSecondary.layoutSelector
        val texts = options.map { it.toLayoutKey() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, texts)
        layoutSelector.setAdapter(adapter)
        layoutSelector.isEnabled = options.isNotEmpty()
        layoutSelector.setText(state.selectedLayout ?: "", false)
    }

    private fun renderVectors(panel: PanelState, frame: SensorFrame) {
        val gyroViews = if (panel.key == "primary") binding.panelPrimary else binding.panelSecondary
        gyroViews.gyroX.text = frame.gyroscope.getOrNull(0)?.format1() ?: "--"
        gyroViews.gyroY.text = frame.gyroscope.getOrNull(1)?.format1() ?: "--"
        gyroViews.gyroZ.text = frame.gyroscope.getOrNull(2)?.format1() ?: "--"
        gyroViews.accX.text = frame.accelerometer.getOrNull(0)?.times(9.8f)?.format1() ?: "--"
        gyroViews.accY.text = frame.accelerometer.getOrNull(1)?.times(9.8f)?.format1() ?: "--"
        gyroViews.accZ.text = frame.accelerometer.getOrNull(2)?.times(9.8f)?.format1() ?: "--"
    }

    private fun Pair<Int, Int>.toLayoutKey(): String = "${first}x$second"
    private fun String.toRowsCols(): Pair<Int, Int>? {
        val parts = split("x")
        if (parts.size != 2) return null
        val r = parts[0].toIntOrNull() ?: return null
        val c = parts[1].toIntOrNull() ?: return null
        return r to c
    }

    private fun Float.format1(): String = String.format(Locale.US, "%.1f", this.toDouble())

    companion object {
        private const val DEFAULT_MIN = 300f
        private const val DEFAULT_MAX = 1000f
    }

    private data class PanelState(
        val key: String,
        var selectedDn: String? = null,
        var selectedLayout: String? = null,
        var mirrorRows: Boolean = false,
        var mirrorCols: Boolean = false,
        var onRender: ((PanelRenderData) -> Unit)? = null,
    )

    private data class PanelRenderData(
        val frame: SensorFrame,
        val rows: Int,
        val cols: Int,
        val values: List<Float>,
        val hasCop: Boolean,
        val copX: Float,
        val copY: Float,
    )
}
