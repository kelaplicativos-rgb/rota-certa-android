package br.com.mapeiaia.rotacerta

object ShortcutActivityLaunchPolicy0176 {
    const val CONTRACT_MARKER = "SHORTCUT_ACTIVITY_LAUNCH_0176"
    const val DISPATCHED_STAGE = "SHORTCUT_ACTIVITY_DISPATCHED_0176"
    const val FAILED_STAGE = "SHORTCUT_ACTIVITY_DISPATCH_FAILED_0176"
    private const val FIRST_REQUEST_CODE = 17_600

    fun usePendingIntent(sdkInt: Int): Boolean = sdkInt >= 34

    fun requestCode(serial: Int): Int {
        val positive = serial and Int.MAX_VALUE
        return if (positive == 0) FIRST_REQUEST_CODE else positive
    }
}
