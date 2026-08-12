-keep class app.tauri.** {
  @app.tauri.JniMethod public <methods>;
  native <methods>;
}

# R8 strips RuntimeVisibleAnnotations and AnnotationDefault from class files by
# default. Plugin discovery is entirely reflection-based (TauriPlugin.permissions,
# Command/ActivityCallback/PermissionCallback method lookup via getAnnotation()/
# isAnnotationPresent() in PluginHandle.indexMethods()), so losing these attributes
# silently breaks every plugin in a minified release build even though the -keep
# rules below still preserve the classes and methods themselves.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keep class app.tauri.plugin.JSArray {
  public <init>(...);
}

-keepclassmembers class org.json.JSONArray {
  public put(...);
}

-keep class app.tauri.plugin.JSObject {
  public <init>(...);
  public put(...);
}

# Invoke is constructed directly (not via reflection) by any code that calls
# into a plugin's Command/ActivityCallback/PermissionCallback methods, which
# can live outside this library's own compilation unit (a plugin author's
# module, a test module, ...). Without an explicit keep, R8 can determine the
# constructor has no caller *it* can see and shrink/inline it away, and
# ObjectMapper is a real parameter type of that constructor -- both produce a
# NoSuchMethodError at the external call site even though the source matches.
-keep class app.tauri.plugin.Invoke {
  public <init>(...);
}

-keep class com.fasterxml.jackson.databind.ObjectMapper { *; }

-keep @app.tauri.annotation.TauriPlugin public class * {
  @app.tauri.annotation.Command public <methods>;
  @app.tauri.annotation.PermissionCallback <methods>;
  @app.tauri.annotation.ActivityCallback <methods>;
  @app.tauri.annotation.Permission <methods>;
  public <init>(...);
}

-keep @app.tauri.annotation.InvokeArg public class * {
  *;
}

-keep @com.fasterxml.jackson.databind.annotation.JsonDeserialize public class * {
  *;
}

-keep @com.fasterxml.jackson.databind.annotation.JsonSerialize public class * {
  *;
}

-keep class * extends com.fasterxml.jackson.databind.JsonDeserializer { *; }

-keep class * extends com.fasterxml.jackson.databind.JsonSerializer { *; }
