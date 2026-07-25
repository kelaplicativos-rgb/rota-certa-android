val androidExtension132 = extensions.getByName("android")
val defaultConfig132 = androidExtension132.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension132)
defaultConfig132.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig132, "0.1.132")
