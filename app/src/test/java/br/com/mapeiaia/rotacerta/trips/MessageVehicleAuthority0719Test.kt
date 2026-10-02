package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageVehicleAuthority0719Test {
    @Test
    fun vehicleResolverNeverTerminatesFallbackWithEmptyProfileResult() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertFalse(source.contains("matching.singleOrNull() ?: return PassengerMessageVehicle0714()"))
        assertFalse(source.contains("?: return PassengerMessageVehicle0714()"))
        assertContains(source, "return rotaCertaFallback0719")
        assertContains(source, "trip?.takeIf { it.vehicleDayConfigured }")
    }

    @Test
    fun everyQuickMessageTemplateUsesTheResolvedVehicleBlock() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val block = source.substringAfter("return when (type) {").substringBefore("\n    }\n}\n\nprivate fun deliverPassengerQuickMessage0656")
        assertContains(block, "PassengerQuickMessageType0656.CONFIRM_NOW")
        assertContains(block, "PassengerQuickMessageType0656.CONFIRM_TOMORROW")
        assertContains(block, "PassengerQuickMessageType0656.CONFIRM_ONE_HOUR")
        assertContains(block, "PassengerQuickMessageType0656.AT_LOCATION")
        assertContains(block, "PassengerQuickMessageType0656.FARE")
        assertTrue(block.split("vehicleBlock").size - 1 >= 5, "all five quick-message templates must consume vehicleBlock")
    }

    @Test
    fun editorPreviewAndExecutionShareTenantAwareTemplateStore() {
        val tools = File("src/main/java/br/com/mapeiaia/rotacerta/RotaCertaTools0172.kt").readText()
        val editor = File("src/main/java/br/com/mapeiaia/rotacerta/MessageTemplatesActivity.kt").readText()
        assertContains(tools, "fun readTrip(context: Context): String = TenantMessageTemplateStore.readTrip(context)")
        assertContains(tools, "fun readValue(context: Context): String = TenantMessageTemplateStore.readValue(context)")
        assertContains(tools, "fun saveTrip(context: Context, value: String) = TenantMessageTemplateStore.saveTrip(context, value)")
        assertContains(tools, "fun saveValue(context: Context, value: String) = TenantMessageTemplateStore.saveValue(context, value)")
        assertContains(editor, "TenantMessageTemplateStore.readTrip(context)")
        assertContains(editor, "TenantMessageTemplateStore.readValue(context)")
    }
}
