# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.foodlabel.studio.**$$serializer { *; }
-keepclassmembers class com.foodlabel.studio.** {
    *** Companion;
}
-keepclasseswithmembers class com.foodlabel.studio.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
# zxing
-dontwarn com.google.zxing.**
