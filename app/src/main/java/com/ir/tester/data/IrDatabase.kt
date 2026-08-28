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
            "WHITEBOARD", "BOARD", "WHITEBOARDS" -> DeviceCategory.WHITEBOARD
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
        val map = categoryBrandsMap[category] ?: return listOf("Samsung", "LG", "Sony", "Philips", "Xiaomi", "TCL", "Hisense", "DEXP", "BBK", "Haier")
        return map.keys.sortedWith(
            compareBy<String> { getBrandPriority(category, it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )
    }

    fun getBrandPowerCodes(category: DeviceCategory, brand: String): List<IrCodeItem> {
        val brandsMap = categoryBrandsMap[category] ?: return emptyList()
        val exact = brandsMap[brand]
        if (exact != null) return exact

        for ((k, v) in brandsMap) {
            if (k.equals(brand, ignoreCase = true)) {
                return v
            }
        }
        return emptyList()
    }

    fun getGlobalCategoryPowerCodes(category: DeviceCategory): List<IrCodeItem> {
        val rawList = globalCodesMap[category] ?: run {
            val collected = ArrayList<IrCodeItem>()
            val brandsMap = categoryBrandsMap[category] ?: return emptyList()
            for ((_, codes) in brandsMap) {
                if (codes.isNotEmpty()) {
                    collected.add(codes.first())
                }
            }
            collected
        }

        return sortCodesByPopularity(category, rawList)
    }

    private fun getBrandPriority(category: DeviceCategory, brand: String): Int {
        val b = brand.trim().lowercase()
        return when (category) {
            DeviceCategory.TV -> when {
                b.contains("samsung") -> 0
                b.contains("lg") -> 1
                b.contains("sony") -> 2
                b.contains("philips") -> 3
                b.contains("xiaomi") || b == "mi" -> 4
                b.contains("tcl") -> 5
                b.contains("hisense") -> 6
                b.contains("dexp") -> 7
                b.contains("bbk") -> 8
                b.contains("haier") -> 9
                b.contains("panasonic") -> 10
                b.contains("toshiba") -> 11
                b.contains("sharp") -> 12
                b.contains("sber") || b.contains("salute") -> 13
                b.contains("kivi") -> 14
                b.contains("yasin") -> 15
                b.contains("telefunken") -> 16
                b.contains("polar") -> 17
                b.contains("supra") -> 18
                b.contains("mystery") -> 19
                b.contains("harper") || b.contains("starwind") -> 20
                b.contains("erisson") || b.contains("vityaz") || b.contains("horizont") -> 21
                b.contains("vizio") || b.contains("rca") || b.contains("insignia") -> 22
                b.contains("skyworth") || b.contains("jvc") || b.contains("akai") -> 23
                b.contains("thomson") || b.contains("grundig") || b.contains("blaupunkt") -> 24
                else -> 100
            }
            DeviceCategory.AC -> when {
                b.contains("daikin") -> 0
                b.contains("mitsubishi") -> 1
                b.contains("gree") -> 2
                b.contains("midea") -> 3
                b.contains("ballu") -> 4
                b.contains("electrolux") -> 5
                b.contains("panasonic") -> 6
                b.contains("haier") -> 7
                b.contains("lg") -> 8
                b.contains("samsung") -> 9
                b.contains("toshiba") -> 10
                b.contains("fujitsu") || b.contains("general") -> 11
                b.contains("hisense") -> 12
                b.contains("tcl") -> 13
                b.contains("aux") -> 14
                b.contains("royal clima") || b.contains("clima") -> 15
                b.contains("kentatsu") -> 16
                b.contains("zanussi") -> 17
                b.contains("carrier") -> 18
                b.contains("hyundai") -> 19
                b.contains("beko") -> 20
                b.contains("hitachi") -> 21
                b.contains("dexp") -> 22
                b.contains("chigo") -> 23
                else -> 100
            }
            DeviceCategory.WHITEBOARD -> when {
                b.contains("irbis") -> 0
                b.contains("promethean") -> 1
                b.contains("smart") -> 2
                b.contains("lumien") -> 3
                b.contains("boxlight") -> 4
                b.contains("philips") -> 5
                b.contains("newline") -> 6
                b.contains("nextouch") -> 7
                b.contains("iqboard") -> 8
                b.contains("teachtouch") -> 9
                b.contains("edusmart") -> 10
                b.contains("donview") || b.contains("horion") -> 11
                else -> 100
            }
            DeviceCategory.SET_TOP_BOX -> when {
                b.contains("xiaomi") || b.contains("mi box") -> 0
                b.contains("apple") -> 1
                b.contains("roku") -> 2
                b.contains("amazon") || b.contains("fire") -> 3
                b.contains("nvidia") || b.contains("shield") -> 4
                b.contains("dune") -> 5
                b.contains("tricolor") || b.contains("триколор") -> 6
                b.contains("rostelecom") || b.contains("ростелеком") || b.contains("wink") -> 7
                b.contains("mts") || b.contains("мтс") -> 8
                b.contains("beeline") || b.contains("билайн") -> 9
                b.contains("dom.ru") || b.contains("дом.ру") || b.contains("movix") -> 10
                b.contains("world vision") || b.contains("selenga") -> 11
                b.contains("dexp") || b.contains("cadena") -> 12
                b.contains("ugoos") || b.contains("tanix") || b.contains("x96") -> 13
                b.contains("mag") || b.contains("infomir") || b.contains("formuler") -> 14
                b.contains("humax") || b.contains("cisco") -> 15
                else -> 100
            }
            DeviceCategory.AUDIO -> when {
                b.contains("sony") -> 0
                b.contains("samsung") -> 1
                b.contains("lg") -> 2
                b.contains("jbl") -> 3
                b.contains("yamaha") -> 4
                b.contains("bose") -> 5
                b.contains("sonos") -> 6
                b.contains("pioneer") -> 7
                b.contains("denon") || b.contains("marantz") -> 8
                b.contains("harman") -> 9
                b.contains("philips") -> 10
                b.contains("edifier") -> 11
                b.contains("sven") || b.contains("microlab") -> 12
                b.contains("panasonic") -> 13
                b.contains("onkyo") || b.contains("technics") -> 14
                b.contains("marshall") -> 15
                b.contains("klipsch") || b.contains("polk") -> 16
                b.contains("jvc") || b.contains("aiwa") || b.contains("kenwood") -> 17
                else -> 100
            }
            DeviceCategory.PROJECTOR -> when {
                b.contains("epson") -> 0
                b.contains("benq") -> 1
                b.contains("optoma") -> 2
                b.contains("xgimi") -> 3
                b.contains("xiaomi") || b.contains("wanbo") -> 4
                b.contains("viewsonic") -> 5
                b.contains("sony") -> 6
                b.contains("nec") -> 7
                b.contains("acer") -> 8
                b.contains("panasonic") -> 9
                b.contains("lg") -> 10
                b.contains("anker") || b.contains("nebula") -> 11
                b.contains("formovie") || b.contains("dangbei") -> 12
                b.contains("infocus") || b.contains("casio") || b.contains("hitachi") -> 13
                b.contains("byintek") || b.contains("touyinger") -> 14
                else -> 100
            }
            DeviceCategory.FAN_HEATER -> when {
                b.contains("dyson") -> 0
                b.contains("xiaomi") || b.contains("smartmi") -> 1
                b.contains("ballu") -> 2
                b.contains("electrolux") -> 3
                b.contains("bork") -> 4
                b.contains("polaris") -> 5
                b.contains("scarlett") -> 6
                b.contains("vitek") -> 7
                b.contains("tefal") || b.contains("rowenta") -> 8
                b.contains("philips") -> 9
                b.contains("midea") -> 10
                b.contains("bionaire") || b.contains("honeywell") || b.contains("lasko") -> 11
                b.contains("stadler") || b.contains("boneco") -> 12
                b.contains("dexp") || b.contains("supra") -> 13
                else -> 100
            }
            DeviceCategory.LED -> when {
                b.contains("rgb") || b.contains("magic") || b.contains("tuya") -> 0
                b.contains("govee") || b.contains("yeelight") -> 1
                b.contains("philips") || b.contains("hue") -> 2
                b.contains("xiaomi") || b.contains("sonoff") -> 3
                b.contains("smart") || b.contains("generic") -> 4
                b.contains("osram") || b.contains("ledvance") -> 5
                b.contains("nanoleaf") || b.contains("lifx") -> 6
                b.contains("ikea") -> 7
                else -> 100
            }
            DeviceCategory.CAMERA -> when {
                b.contains("canon") -> 0
                b.contains("nikon") -> 1
                b.contains("sony") -> 2
                b.contains("fujifilm") || b.contains("fuji") -> 3
                b.contains("olympus") -> 4
                b.contains("panasonic") || b.contains("lumix") -> 5
                b.contains("pentax") -> 6
                b.contains("leica") -> 7
                b.contains("sigma") || b.contains("hasselblad") -> 8
                else -> 100
            }
        }
    }

    private fun sortCodesByPopularity(category: DeviceCategory, codes: List<IrCodeItem>): List<IrCodeItem> {
        return codes.sortedWith(
            compareBy<IrCodeItem> { getBrandPriority(category, it.brand) }
                .thenBy { it.brand.lowercase() }
                .thenBy { it.modelOrDescription.lowercase() }
        )
    }

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
