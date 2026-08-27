package com.ir.tester.data
import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList
object IrDatabase {
    private val categoryBrandsMap = HashMap<DeviceCategory, MutableMap<String, MutableList<IrCodeItem>>>()
    private val globalCodesMap = HashMap<DeviceCategory, MutableList<IrCodeItem>>()
    @Volatile
    private var isInitialized = false
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val assetManager = context.assets
                val inputStream = assetManager.open("flipper_database.json")
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val jsonString = reader.readText()
                reader.close()
                inputStream.close()
                val rootObj = JSONObject(jsonString)
                // 1. Categories and Brands
                val categoriesObj = rootObj.getJSONObject("categories")
                for (catKey in categoriesObj.keys()) {
                    val devCat = parseCategory(catKey) ?: continue
                    val brandsObj = categoriesObj.getJSONObject(catKey)
                    val brandsMap = categoryBrandsMap.getOrPut(devCat) { LinkedHashMap() }
                    for (brandKey in brandsObj.keys()) {
                        val codesArray = brandsObj.getJSONArray(brandKey)
                        val codeList = ArrayList<IrCodeItem>()
                        for (i in 0 until codesArray.length()) {
                            val itemObj = codesArray.getJSONObject(i)
                            val patArray = itemObj.getJSONArray("pattern")
                            val pattern = IntArray(patArray.length())
                            for (p in 0 until patArray.length()) {
                                pattern[p] = patArray.getInt(p)
                            }
                            codeList.add(
                                IrCodeItem(
                                    id = itemObj.optString("id", "${catKey}_${brandKey}_$i"),
                                    brand = itemObj.optString("brand", brandKey),
                                    modelOrDescription = itemObj.optString("model", "Power"),
                                    category = devCat,
                                    protocol = parseProtocol(itemObj.optString("protocol", "NEC")),
                                    frequency = itemObj.optInt("frequency", 38000),
                                    pattern = pattern
                                )
                            )
                        }
                        brandsMap[brandKey] = codeList
                    }
                }
                // 2. Global Bruteforce codes
                val globalObj = rootObj.getJSONObject("global_codes")
                for (catKey in globalObj.keys()) {
                    val devCat = parseCategory(catKey) ?: continue
                    val codesArray = globalObj.getJSONArray(catKey)
                    val list = globalCodesMap.getOrPut(devCat) { ArrayList() }
                    for (i in 0 until codesArray.length()) {
                        val itemObj = codesArray.getJSONObject(i)
                        val patArray = itemObj.getJSONArray("pattern")
                        val pattern = IntArray(patArray.length())
                        for (p in 0 until patArray.length()) {
                            pattern[p] = patArray.getInt(p)
                        }
                        list.add(
                            IrCodeItem(
                                id = itemObj.optString("id", "glob_${catKey}_$i"),
                                brand = itemObj.optString("brand", "Universal"),
                                modelOrDescription = itemObj.optString("model", "Power Code"),
                                category = devCat,
                                protocol = parseProtocol(itemObj.optString("protocol", "NEC")),
                                frequency = itemObj.optInt("frequency", 38000),
                                pattern = pattern
                            )
                        )
                    }
                }
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
                initFallback()
                isInitialized = true
            }
        }
    }
    private fun parseCategory(name: String): DeviceCategory? {
        return when (name.uppercase()) {
            "TV" -> DeviceCategory.TV
            "AC" -> DeviceCategory.AC
            "SET_TOP_BOX" -> DeviceCategory.SET_TOP_BOX
            "AUDIO" -> DeviceCategory.AUDIO
            "PROJECTOR" -> DeviceCategory.PROJECTOR
            "FAN_HEATER" -> DeviceCategory.FAN_HEATER
            "LED" -> DeviceCategory.LED
            "CAMERA" -> DeviceCategory.CAMERA
            else -> null
        }
    }
    private fun parseProtocol(name: String): ProtocolType {
        return when (name.uppercase()) {
            "SAMSUNG" -> ProtocolType.SAMSUNG
            "SONY_SIRC", "SIRC" -> ProtocolType.SONY_SIRC
            "RC5" -> ProtocolType.RC5
            "RC6" -> ProtocolType.RC6
            "RAW" -> ProtocolType.RAW
            else -> ProtocolType.NEC
        }
    }
    fun getCategories(): List<DeviceCategory> {
        return DeviceCategory.values().toList()
    }
    fun getBrandsForCategory(category: DeviceCategory): List<String> {
        val map = categoryBrandsMap[category] ?: return listOf("Samsung", "LG", "Sony", "Philips", "DEXP", "BBK", "Xiaomi", "Haier")
        return map.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    fun getBrandPowerCodes(category: DeviceCategory, brand: String): List<IrCodeItem> {
        val brandsMap = categoryBrandsMap[category] ?: return emptyList()
        val exact = brandsMap[brand]
        if (exact != null) return exact
        // Case-insensitive lookup
        for ((k, v) in brandsMap) {
            if (k.equals(brand, ignoreCase = true)) {
                return v
            }
        }
        return emptyList()
    }
    fun getGlobalCategoryPowerCodes(category: DeviceCategory): List<IrCodeItem> {
        val list = globalCodesMap[category]
        if (!list.isNullOrEmpty()) return list
        val collected = ArrayList<IrCodeItem>()
        val brandsMap = categoryBrandsMap[category] ?: return emptyList()
        for ((_, codes) in brandsMap) {
            if (codes.isNotEmpty()) {
                collected.add(codes.first())
            }
        }
        return collected
    }
    // =========================================================================
    // FALLBACK INITIALIZATION
    // =========================================================================
    private fun initFallback() {
        val tvMap = categoryBrandsMap.getOrPut(DeviceCategory.TV) { LinkedHashMap() }
        val tvList = ArrayList<IrCodeItem>()
        val samsungCode = IrCodeItem("fb_samsung", "Samsung", "Smart TV BN59", DeviceCategory.TV, ProtocolType.SAMSUNG, 38000, encodeSamsung(0x0707, 0x02))
        val lgCode = IrCodeItem("fb_lg", "LG", "webOS AKB", DeviceCategory.TV, ProtocolType.NEC, 38000, encodeNec(0x04, 0x08))
        val sonyCode = IrCodeItem("fb_sony", "Sony", "Bravia RMT", DeviceCategory.TV, ProtocolType.SONY_SIRC, 40000, encodeSony(0x15, 0x01, 12))
        tvMap["Samsung"] = mutableListOf(samsungCode)
        tvMap["LG"] = mutableListOf(lgCode)
        tvMap["Sony"] = mutableListOf(sonyCode)
        tvList.add(samsungCode)
        tvList.add(lgCode)
        tvList.add(sonyCode)
        globalCodesMap[DeviceCategory.TV] = tvList
    }
    fun encodeNec(address: Int, command: Int): IntArray {
        val pattern = ArrayList<Int>()
        pattern.add(9000); pattern.add(4500)
        for (i in 0 until 8) {
            val bit = (address shr i) and 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        for (i in 0 until 8) {
            val bit = ((address shr i) and 1) xor 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        for (i in 0 until 8) {
            val bit = (command shr i) and 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        for (i in 0 until 8) {
            val bit = ((command shr i) and 1) xor 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        pattern.add(560)
        return pattern.toIntArray()
    }
    fun encodeSamsung(address: Int, command: Int): IntArray {
        val pattern = ArrayList<Int>()
        pattern.add(4500); pattern.add(4500)
        val addrLo = address and 0xFF
        for (i in 0 until 8) {
            val bit = (addrLo shr i) and 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        for (i in 0 until 8) {
            val bit = (addrLo shr i) and 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        for (i in 0 until 8) {
            val bit = (command shr i) and 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        for (i in 0 until 8) {
            val bit = ((command shr i) and 1) xor 1
            pattern.add(560); pattern.add(if (bit == 1) 1690 else 560)
        }
        pattern.add(560)
        return pattern.toIntArray()
    }
    fun encodeSony(command: Int, address: Int, bitCount: Int = 12): IntArray {
        val pattern = ArrayList<Int>()
        pattern.add(2400); pattern.add(600)
        for (i in 0 until 7) {
            val bit = (command shr i) and 1
            pattern.add(if (bit == 1) 1200 else 600); pattern.add(600)
        }
        val addressBits = bitCount - 7
        for (i in 0 until addressBits) {
            val bit = (address shr i) and 1
            pattern.add(if (bit == 1) 1200 else 600); pattern.add(600)
        }
        return pattern.toIntArray()
    }
}
