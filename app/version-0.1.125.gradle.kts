val androidExtension125 = extensions.getByName("android")
val defaultConfig125 = androidExtension125.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension125)
defaultConfig125.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig125, "0.1.125")
