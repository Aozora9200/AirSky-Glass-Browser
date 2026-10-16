# JAR 実行ツール: 変換した JAR は互換レイヤー(java.awt / javax.swing の代わり)を名前で呼ぶので、名前も中身も残す
-keep class com.airsky.browser.jvm.** { *; }
# 端末の上で JAR を DEX に変換する D8(中で ServiceLoader やリソースを名前で使う)
-keep class com.android.tools.r8.** { *; }
-dontwarn com.android.tools.r8.**
-dontwarn org.objectweb.asm.**
-keep class org.mozilla.gecko.AndroidGamepadManager { *; }
