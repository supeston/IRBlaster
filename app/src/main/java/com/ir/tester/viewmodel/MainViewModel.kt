package com.ir.tester.viewmodel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ir.tester.data.DeviceCategory
import com.ir.tester.data.IrCodeItem
import com.ir.tester.data.IrDatabase
import com.ir.tester.engine.GeneratorMetrics
import com.ir.tester.engine.IrSignalEngine
import com.ir.tester.engine.ModulationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
data class UniversalStepState(
    val step: Int = 0, // 0: Select Category, 1: Select Brand, 2: Fire / Transmit
    val selectedCategory: DeviceCategory? = null,
    val availableBrands: List<String> = emptyList(),
    val selectedBrand: String = "",
    val brandCodes: List<IrCodeItem> = emptyList(),
    val isScanning: Boolean = false,
    val sentCount: Int = 0,
    val totalCount: Int = 0,
    val currentItem: IrCodeItem? = null
)
data class GlobalStepState(
    val step: Int = 0, // 0: Select Category, 1: Action Screen
    val selectedCategory: DeviceCategory? = null,
    val codesList: List<IrCodeItem> = emptyList(),
    val isScanning: Boolean = false,
    val sentCount: Int = 0,
    val totalCount: Int = 0,
    val currentItem: IrCodeItem? = null
)
data class SimpleJammerState(
    val selectedFrequency: Int = 38000,
    val selectedMode: ModulationMode = ModulationMode.SWEEP,
    val isRunning: Boolean = false,
    val packetsSent: Long = 0L,
    val formattedTime: String = "00:00"
)
data class MainUiState(
    val hasIrEmitter: Boolean = false,
    val isUsbDongleConnected: Boolean = false,
    val isDemoMode: Boolean = false,
    val selectedTab: Int = 0,
    val universalState: UniversalStepState = UniversalStepState(),
    val globalState: GlobalStepState = GlobalStepState(),
    val jammerState: SimpleJammerState = SimpleJammerState(),
    val feedbackMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = IrSignalEngine(application.applicationContext)
    private val _uiState = MutableStateFlow(
        MainUiState(
            hasIrEmitter = engine.hasEmitter,
            isUsbDongleConnected = engine.usbIrManager.isConnected.value
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var universalScanJob: Job? = null
    private var globalScanJob: Job? = null

    init {
        IrDatabase.init(application.applicationContext)
        viewModelScope.launch {
            engine.usbIrManager.isConnected.collect { isUsb ->
                _uiState.update { state ->
                    state.copy(
                        hasIrEmitter = engine.hasEmitter,
                        isUsbDongleConnected = isUsb
                    )
                }
            }
        }
        viewModelScope.launch {
            engine.metrics.collect { m ->
                _uiState.update { state ->
                    state.copy(
                        jammerState = state.jammerState.copy(
                            isRunning = m.isRunning,
                            packetsSent = m.packetsSent,
                            formattedTime = m.formattedTime
                        )
                    )
                }
            }
        }
    }
    fun checkIrEmitter() {
        _uiState.update { it.copy(hasIrEmitter = engine.hasEmitter) }
    }
    fun enableDemoMode() {
        _uiState.update { it.copy(isDemoMode = true) }
    }
    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
    // =========================================================================
    // 1. УНИВЕРСАЛ: ПОШАГОВЫЙ МАСТЕР (ШАГ 1 -> ШАГ 2 -> ШАГ 3)
    // =========================================================================
    fun selectUniversalCategory(category: DeviceCategory) {
        stopUniversalScan()
        val brands = IrDatabase.getBrandsForCategory(category)
        _uiState.update { state ->
            state.copy(
                universalState = state.universalState.copy(
                    step = 1,
                    selectedCategory = category,
                    availableBrands = brands,
                    selectedBrand = "",
                    brandCodes = emptyList(),
                    sentCount = 0,
                    totalCount = 0,
                    currentItem = null
                )
            )
        }
    }
    fun selectUniversalBrand(brand: String) {
        stopUniversalScan()
        val cat = _uiState.value.universalState.selectedCategory ?: DeviceCategory.TV
        val codes = IrDatabase.getBrandPowerCodes(cat, brand)
        _uiState.update { state ->
            state.copy(
                universalState = state.universalState.copy(
                    step = 2,
                    selectedBrand = brand,
                    brandCodes = codes,
                    totalCount = codes.size,
                    sentCount = 0,
                    currentItem = null
                )
            )
        }
    }
    fun backUniversalToCategories() {
        stopUniversalScan()
        _uiState.update { state ->
            state.copy(
                universalState = state.universalState.copy(
                    step = 0,
                    selectedCategory = null,
                    selectedBrand = "",
                    brandCodes = emptyList()
                )
            )
        }
    }
    fun backUniversalToBrands() {
        stopUniversalScan()
        _uiState.update { state ->
            state.copy(
                universalState = state.universalState.copy(
                    step = 1,
                    selectedBrand = "",
                    brandCodes = emptyList()
                )
            )
        }
    }
    fun toggleUniversalScan() {
        if (_uiState.value.universalState.isScanning) {
            stopUniversalScan()
        } else {
            startUniversalScan()
        }
    }
    private fun startUniversalScan() {
        if (universalScanJob?.isActive == true) return
        val codes = _uiState.value.universalState.brandCodes
        if (codes.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                universalState = state.universalState.copy(
                    isScanning = true,
                    sentCount = 0,
                    totalCount = codes.size
                )
            )
        }
        universalScanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                for ((index, item) in codes.withIndex()) {
                    if (!isActive) break
                    _uiState.update { state ->
                        state.copy(
                            universalState = state.universalState.copy(
                                currentItem = item,
                                sentCount = index + 1
                            )
                        )
                    }
                    engine.transmitSingle(item.frequency, item.pattern)
                    delay(60) // Физически оптимальная пауза без необходимости настраивать слайдер
                }
            } finally {
                _uiState.update { state ->
                    state.copy(
                        universalState = state.universalState.copy(isScanning = false)
                    )
                }
            }
        }
    }
    fun stopUniversalScan() {
        universalScanJob?.cancel()
        universalScanJob = null
        _uiState.update { state ->
            state.copy(
                universalState = state.universalState.copy(isScanning = false)
            )
        }
    }
    // =========================================================================
    // 2. БРУТ: ГЛОБАЛЬНЫЙ ПЕРЕБОР
    // =========================================================================
    fun selectGlobalCategory(category: DeviceCategory) {
        stopGlobalScan()
        val codes = IrDatabase.getGlobalCategoryPowerCodes(category)
        _uiState.update { state ->
            state.copy(
                globalState = state.globalState.copy(
                    step = 1,
                    selectedCategory = category,
                    codesList = codes,
                    totalCount = codes.size,
                    sentCount = 0,
                    currentItem = null
                )
            )
        }
    }
    fun backGlobalToCategories() {
        stopGlobalScan()
        _uiState.update { state ->
            state.copy(
                globalState = state.globalState.copy(
                    step = 0,
                    selectedCategory = null,
                    codesList = emptyList()
                )
            )
        }
    }
    fun toggleGlobalScan() {
        if (_uiState.value.globalState.isScanning) {
            stopGlobalScan()
        } else {
            startGlobalScan()
        }
    }
    private fun startGlobalScan() {
        if (globalScanJob?.isActive == true) return
        val codes = _uiState.value.globalState.codesList
        if (codes.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                globalState = state.globalState.copy(
                    isScanning = true,
                    sentCount = 0,
                    totalCount = codes.size
                )
            )
        }
        globalScanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                for ((index, item) in codes.withIndex()) {
                    if (!isActive) break
                    _uiState.update { state ->
                        state.copy(
                            globalState = state.globalState.copy(
                                currentItem = item,
                                sentCount = index + 1
                            )
                        )
                    }
                    engine.transmitSingle(item.frequency, item.pattern)
                    delay(60)
                }
            } finally {
                _uiState.update { state ->
                    state.copy(
                        globalState = state.globalState.copy(isScanning = false)
                    )
                }
            }
        }
    }
    fun stopGlobalScan() {
        globalScanJob?.cancel()
        globalScanJob = null
        _uiState.update { state ->
            state.copy(
                globalState = state.globalState.copy(isScanning = false)
            )
        }
    }
    // =========================================================================
    // 3. ДЖАММЕР (ГЕНЕРАТОР ПОМЕХ) - МАКСИМАЛЬНО ПРОСТО
    // =========================================================================
    fun setJammerFrequency(freq: Int) {
        engine.carrierFrequency = freq
        _uiState.update { state ->
            state.copy(
                jammerState = state.jammerState.copy(selectedFrequency = freq)
            )
        }
    }
    fun setJammerMode(mode: ModulationMode) {
        engine.modulationMode = mode
        _uiState.update { state ->
            state.copy(
                jammerState = state.jammerState.copy(selectedMode = mode)
            )
        }
    }
    fun toggleJammer() {
        if (_uiState.value.jammerState.isRunning) {
            engine.stop()
        } else {
            engine.carrierFrequency = _uiState.value.jammerState.selectedFrequency
            engine.modulationMode = _uiState.value.jammerState.selectedMode
            engine.signalDensity = 10
            engine.sweepSpeed = 5
            engine.start(viewModelScope)
        }
    }
    fun transmitSingleCode(item: IrCodeItem) {
        viewModelScope.launch(Dispatchers.Default) {
            val success = engine.transmitSingle(item.frequency, item.pattern)
            _uiState.update { state ->
                state.copy(
                    feedbackMessage = if (success || state.isDemoMode) {
                        "Отправлено: ${item.brand}"
                    } else {
                        "Ошибка передачи ИК сигнала"
                    }
                )
            }
        }
    }
    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
    override fun onCleared() {
        super.onCleared()
        stopUniversalScan()
        stopGlobalScan()
        engine.stop()
    }
}
