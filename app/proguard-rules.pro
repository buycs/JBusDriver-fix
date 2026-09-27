# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Develop\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original type file name.
#-renamesourcefileattribute SourceFile

# 保持枚举 enum 类不被混淆
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin
-dontwarn kotlin.**

##################below is for common android
-keep public class **.R$* { public static final int *; }
-keep public class * extends android.app.Activity

-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class com.android.vending.licensing.ILicensingService

-keep public class * extends androidx.**
-keep public class * extends com.google.android.material.**

# 保留自定义控件不能被混淆
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(***);
    *** get* ();
}
# 保留Parcelable序列化的类不能被混淆
-keep class * implements android.os.Parcelable{
    public static final android.os.Parcelable$Creator *;
}
# 保留Serializable 序列化的类不被混淆
-keepclassmembers class * implements java.io.Serializable {
   static final long serialVersionUID;
   private static final java.io.ObjectStreamField[] serialPersistentFields;
   !static !transient <fields>;
   private void writeObject(java.io.ObjectOutputStream);
   private void readObject(java.io.ObjectInputStream);
   java.lang.Object writeReplace();
   java.lang.Object readResolve();
}
#bean 不能被混淆
-keep class me.jbusdriver.*.bean.** {*;}
#枚举
-keep class me.jbusdriver.ui.data.enums.** {*;}


# 对R文件下的所有类及其方法，都不能被混淆
-keep class .R
-keepclassmembers class **.R$* {
    *;
}
    ##################upper is for common android


#okhttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
#okhttp end

# Retrofit
-dontwarn okio.**
-dontwarn javax.annotation.**
# Retrofit end

#glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
#glide end



#gson

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { *; }

# Prevent proguard from stripping interface information from TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

##---------------End: proguard configuration for Gson  ----------

#gson end

# Jsoup
-keep public class org.jsoup.** {
    public *;
}
# Jsou end

#umeng
-keep class com.umeng.** {*;}

-keepclassmembers class * {
   public <init> (org.json.JSONObject);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class me.jbusdriver.**.R$*{
public static final int *;
}
#umeng end


#BaseRecyclerViewAdapterHelper
-keep class com.chad.library.adapter.** {
*;
}
-keep public class * extends com.chad.library.adapter.base.BaseQuickAdapter
-keep public class * extends com.chad.library.adapter.base.BaseViewHolder
-keepclassmembers  class **$** extends com.chad.library.adapter.base.BaseViewHolder {
     <init>(...);
}
#BaseRecyclerViewAdapterHelper end

#BubbleSeekBar
-keep class com.xw.repo.BubbleSeekBar {
*;
}
#BubbleSeekBar end

#ImmersionBar
 -keep class com.gyf.barlibrary.* {*;}
#ImmersionBar end


#hotfix
#-dontwarn com.tencent.bugly.**
#-keep public class com.tencent.bugly.**{*;}
# tinker混淆规则
#-dontwarn com.tencent.tinker.**
#-keep class com.tencent.tinker.** { *; }
#hotfix end


#-keep class me.jbusdriver.http.** { *; }
# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---- Media3 / ExoPlayer ----
# media3 各构件自带 consumer 规则, 这里只压掉可选的扩展渲染器缺省告警。
-dontwarn androidx.media3.**

# ---- libtorrent4j (磁力播放的 BT 引擎) ----
# 原生层按「类名 + 方法签名」通过 JNI 回调, 混淆后 System.loadLibrary 能过,
# 但一建 session / 一收 alert 就 NoSuchMethodError。整体保留。
-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.** {
    native <methods>;
    public <init>(...);
}
-dontwarn org.libtorrent4j.**

# 我们自己挂在引擎上的监听器会被 libtorrent4j 从 native 线程回调，类名与方法名不能改。
#
# 只有 TorrentSession 里那个匿名 AlertListener（`TorrentSession$alertListener$1`）在回调路径上，
# 所以只保留这一个类族（`$*` 覆盖内部类）。
# 包里的其余东西 —— MagnetDataSource / MagnetPlayback / MagnetStream / MagnetUnavailable /
# MagnetStreamPolicyKt / MagnetPlaybackException —— 全都是 Kotlin 直接 new 或直接调，
# 不参与 JNI，正常混淆即可，不必整包保留。
-keep class me.jbusdriver.torrent.TorrentSession { *; }
-keep class me.jbusdriver.torrent.TorrentSession$* { *; }

