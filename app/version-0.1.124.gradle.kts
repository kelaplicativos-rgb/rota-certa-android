val androidExtension124 = extensions.getByName("android")
val defaultConfig124 = androidExtension124.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension124)
defaultConfig124.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig124, "0.1.124")
