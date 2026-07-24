val androidExtension135 = extensions.getByName("android")
val defaultConfig135 = androidExtension135.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension135)
defaultConfig135.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig135, "0.1.135")
