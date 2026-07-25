val androidExtension136 = extensions.getByName("android")
val defaultConfig136 = androidExtension136.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension136)
defaultConfig136.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig136, "0.1.136")
