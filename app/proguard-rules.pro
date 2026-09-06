# Modelos da API sao desserializados por reflexao do kotlinx.serialization.
-keepclassmembers class br.com.soe.campo.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class br.com.soe.campo.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit/OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
