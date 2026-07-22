val androidExtension123 = extensions.getByName("android")
val defaultConfig123 = androidExtension123.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension123)
defaultConfig123.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig123, "0.1.123")
