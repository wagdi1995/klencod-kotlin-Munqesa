# الأقسام (Fragments) تُنشأ عبر الاسم عند استعادة الحالة، لذلك نُبقي على المُنشئ الافتراضي
-keep class com.munqesa.app.ui.*Fragment { <init>(); }

# العناصر المخصصة المستخدمة في ملفات التخطيط (XML)
-keep class com.munqesa.app.ui.LiquidGlassBottomNav { <init>(...); }
-keep class com.munqesa.app.ui.ZoomLayout { <init>(...); }

# الاحتفاظ بأرقام الأسطر لتسهيل قراءة الأخطاء
-keepattributes SourceFile,LineNumberTable
