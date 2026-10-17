# App-specific R8 rules. Libraries ship their own consumer rules; add rules here only
# for code R8 cannot see is used (reflection, JNI), and say why next to each rule.

# Glance creates widget ActionCallbacks by reflection (Class.newInstance) when a widget button is
# tapped. Glance's own rule keeps the classes but not their constructors, so R8 full mode drops
# them and every widget button fails with NoSuchMethodException.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
