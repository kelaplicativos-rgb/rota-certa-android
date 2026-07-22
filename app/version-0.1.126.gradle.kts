val androidExtension126 = extensions.getByName("android")
val defaultConfig126 = androidExtension126.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension126)
defaultConfig126.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig126, "0.1.126")
