package com.example.e_textilesendingserver.ui.visualization

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.e_textilesendingserver.R
import com.example.e_textilesendingserver.core.parser.SensorFrame
import com.example.e_textilesendingserver.data.FramePreviewStore
import com.example.e_textilesendingserver.databinding.ActivityVisualizationBinding
import com.example.e_textilesendingserver.ui.widget.CopBoardView
import com.example.e_textilesendingserver.ui.widget.HeatmapView
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.Locale

class VisualizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisualizationBinding
    private val panelA = PanelState("primary")
    private val panelB = PanelState("secondary")
    private var collectJob: Job? = null
    private var dualMode = false
    private var lastDeviceDns: List<String> = emptyList()
    private var initialOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialOrientation = savedInstanceState?.getInt(STATE_INITIAL_ORIENTATION) ?: requestedOrientation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityVisualizationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()
        setupToolbar()
        setupControls()
    }

    @OptIn(FlowPreview::class)
    override fun onStart() {
        super.onStart()
        FramePreviewStore.setEnabled(true)
        collectJob = lifecycleScope.launch {
            FramePreviewStore.state
                .sample(FRAME_SAMPLE_MS)
                .collectLatest { map ->
                    val now = System.currentTimeMillis()
                    val fresh = map.filterValues { now - it.receivedAt <= DEVICE_STALE_MS }
                    val devices = fresh.keys.sorted()
                    updateDeviceSelectors(devices)
                    updatePanels(fresh)
                }
        }
    }

    override fun onStop() {
        collectJob?.cancel()
        collectJob = null
        FramePreviewStore.setEnabled(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_INITIAL_ORIENTATION, initialOrientation)
        super.onSaveInstanceState(outState)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        title = getString(R.string.visual_title)
    }

    private fun setupControls() {
        binding.dualSwitch.setOnCheckedChangeListener { _, checked ->
            applyDualMode(checked)
        }
        applyDualMode(binding.dualSwitch.isChecked)
        binding.resetRangeButton.setOnClickListener {
            binding.pressureMin.setText(DEFAULT_MIN.toString())
            binding.pressureMax.setText(DEFAULT_MAX.toString())
        }
        setupPanelUi(panelA, binding.panelPrimary.telemetryHeatmap, binding.panelPrimary.copBoard)
        setupPanelUi(panelB, binding.panelSecondary.telemetryHeatmap, binding.panelSecondary.copBoard)
    }

    private fun applyWindowInsets() {
        val toolbarInitialTop = binding.toolbar.paddingTop
        val contentInitialBottom = binding.contentScroll.paddingBottom
        val contentInitialTopMargin = (binding.contentScroll.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = toolbarInitialTop + insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentScroll) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = contentInitialBottom + insets.bottom)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = contentInitialTopMargin + insets.top
            }
            windowInsets
        }
    }

    private fun applyDualMode(enabled: Boolean) {
        dualMode = enabled
        binding.panelSecondary.root.isVisible = enabled
        binding.panelContainer.orientation = if (enabled) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        updatePanelWeights(enabled)
        requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            initialOrientation
        }
    }

    private fun updatePanelWeights(horizontal: Boolean) {
        val primaryParams = binding.panelPrimary.root.layoutParams as LinearLayout.LayoutParams
        val secondaryParams = binding.panelSecondary.root.layoutParams as LinearLayout.LayoutParams
        if (horizontal) {
            primaryParams.width = 0
            secondaryParams.width = 0
            primaryParams.weight = 1f
            secondaryParams.weight = 1f
        } else {
            primaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            secondaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            primaryParams.weight = 0f
            secondaryParams.weight = 0f
        }
        binding.panelPrimary.root.layoutParams = primaryParams
        binding.panelSecondary.root.layoutParams = secondaryParams
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
        val sorted = dns.sorted()
        if (sorted == lastDeviceDns) return
        lastDeviceDns = sorted
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sorted)
        binding.panelPrimary.deviceSelector.setAdapter(adapter)
        binding.panelSecondary.deviceSelector.setAdapter(adapter)
        if (sorted.isEmpty()) {
            panelA.selectedDn = null
            panelB.selectedDn = null
            binding.panelPrimary.deviceSelector.setText("", false)
            binding.panelSecondary.deviceSelector.setText("", false)
            return
        }
        val primaryDn = panelA.selectedDn?.takeIf { sorted.contains(it) } ?: sorted.first()
        val secondaryDn = panelB.selectedDn?.takeIf { sorted.contains(it) }
            ?: sorted.getOrNull(1)
            ?: primaryDn
        panelA.selectedDn = primaryDn
        panelB.selectedDn = secondaryDn
        if (binding.panelPrimary.deviceSelector.text.toString() != primaryDn) {
            binding.panelPrimary.deviceSelector.setText(primaryDn, false)
        }
        if (binding.panelSecondary.deviceSelector.text.toString() != secondaryDn) {
            binding.panelSecondary.deviceSelector.setText(secondaryDn, false)
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
        if (options != state.lastLayouts) {
            val texts = options.map { it.toLayoutKey() }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, texts)
            layoutSelector.setAdapter(adapter)
            state.lastLayouts = options
        }
        layoutSelector.isEnabled = options.isNotEmpty()
        val textValue = state.selectedLayout ?: ""
        if (layoutSelector.text.toString() != textValue) {
            layoutSelector.setText(textValue, false)
        }
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
        private const val STATE_INITIAL_ORIENTATION = "visual_initial_orientation"
        private const val DEFAULT_MIN = 300f
        private const val DEFAULT_MAX = 1000f
        private const val FRAME_SAMPLE_MS = 32L
        private const val DEVICE_STALE_MS = 5_000L
    }

    private data class PanelState(
        val key: String,
        var selectedDn: String? = null,
        var selectedLayout: String? = null,
        var mirrorRows: Boolean = false,
        var mirrorCols: Boolean = false,
        var lastLayouts: List<Pair<Int, Int>> = emptyList(),
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
