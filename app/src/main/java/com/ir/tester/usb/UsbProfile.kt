package com.ir.tester.usb

data class UsbDongleProfile(
    val name: String,
    val encoderFactory: () -> UsbIrEncoder
)

object UsbProfiles {
    private val table = mapOf(
        (0x10C4 to 0x8468) to UsbDongleProfile("ZaZa Remote / Tiqiaa / Tview") { LegacyBulkStEncoder() },
        (0x045E to 0x8468) to UsbDongleProfile("ZaZa Remote / Tiqiaa / Tview") { LegacyBulkStEncoder() },
        (0x045C to 0x0131) to UsbDongleProfile("ElkSmart D552/D226") { ElkSmartBulkEncoder() },
        (0x045C to 0x0132) to UsbDongleProfile("ElkSmart D552/D226") { ElkSmartBulkEncoder() },
        (0x045C to 0x0134) to UsbDongleProfile("Ocrustar / ElkSmart D552/D226") { ElkSmartBulkEncoder() },
        (0x045C to 0x014A) to UsbDongleProfile("ElkSmart D552/D226") { ElkSmartBulkEncoder() },
        (0x045C to 0x0184) to UsbDongleProfile("ElkSmart D552/D226") { ElkSmartBulkEncoder() },
        (0x045C to 0x0195) to UsbDongleProfile("ElkSmart D552/D226") { ElkSmartBulkEncoder() },
        (0x045C to 0x02AA) to UsbDongleProfile("ElkSmart old model") { ElkSmartBulkEncoder() }
    )

    fun find(vid: Int, pid: Int): UsbDongleProfile? = table[vid to pid]
    fun isKnown(vid: Int, pid: Int): Boolean = table.containsKey(vid to pid)
}
