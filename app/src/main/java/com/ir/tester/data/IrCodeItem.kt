package com.ir.tester.data
enum class DeviceCategory(val displayName: String, val singleName: String) {
    TV("телики", "телик"),
    AC("кондеры", "кондер"),
    WHITEBOARD("доски", "доска"),
    SET_TOP_BOX("приставки", "приставка"),
    AUDIO("саундбары", "саундбар"),
    PROJECTOR("проекторы", "проектор"),
    FAN_HEATER("вентили", "вентиль"),
    LED("led ленты", "led лента"),
    CAMERA("фотики", "фотик")
}
enum class ProtocolType {
    NEC,
    SAMSUNG,
    SONY_SIRC,
    RC5,
    RC6,
    RAW
}
enum class ButtonType {
    POWER,
    POWER_ON,
    POWER_OFF,
    MUTE,
    VOL_UP,
    VOL_DOWN,
    CH_UP,
    CH_DOWN,
    NAV_UP,
    NAV_DOWN,
    NAV_LEFT,
    NAV_RIGHT,
    NAV_OK,
    BACK,
    HOME,
    MENU,
    SOURCE,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    TEMP_UP,
    TEMP_DOWN,
    MODE,
    FAN_SPEED,
    SWING,
    SHUTTER,
    CUSTOM
}
data class IrButton(
    val name: String,
    val type: ButtonType,
    val frequency: Int = 38000,
    val pattern: IntArray,
    val protocol: ProtocolType = ProtocolType.NEC
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IrButton
        return name == other.name && type == other.type && frequency == other.frequency
    }
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + frequency
        return result
    }
}
data class IrDevice(
    val id: String,
    val brand: String,
    val model: String,
    val category: DeviceCategory,
    val buttons: List<IrButton>
) {
    val displayName: String
        get() = "$brand $model".lowercase()
    val powerButtons: List<IrButton>
        get() = buttons.filter {
            it.type == ButtonType.POWER || it.type == ButtonType.POWER_OFF || it.type == ButtonType.POWER_ON
        }
}
data class IrCodeItem(
    val id: String,
    val brand: String,
    val modelOrDescription: String,
    val category: DeviceCategory,
    val protocol: ProtocolType,
    val frequency: Int,
    val pattern: IntArray
) {
    val summary: String
        get() = "$brand - $modelOrDescription".lowercase()
    val protocolLabel: String
        get() = "${protocol.name.lowercase()} • ${frequency / 1000} кгц"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IrCodeItem
        return id == other.id
    }
    override fun hashCode(): Int {
        return id.hashCode()
    }
}
