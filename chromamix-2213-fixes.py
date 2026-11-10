from pathlib import Path
root=Path("modsrc/src/main/java/dev/chromamix/extra")
p=root/"RgbRules.java"
s=p.read_text()
s=s.replace('"minecraft:white_banner", "minecraft:white_wall_banner", "minecraft:white_shulker_box",','"minecraft:white_banner", "minecraft:white_shulker_box",')
s=s.replace('"create:white_valve_handle", "create:white_table_cloth"','"create:white_valve_handle", "create:white_table_cloth", "create:white_nixie_tube", "create:white_sail"')
insert='''  /** Existing 15-color Create recipes stay registered; output takes the actual RGB pigment color. */
  public static boolean matchesLegacyColorRecipe(ItemStack source, ItemStack result) {
    if (source.isEmpty() || result.isEmpty() || !accepts(source)) return false;
    String in=id(source), out=id(result);
    int colon=in.indexOf(':');
    if (colon<0 || !in.substring(colon+1).startsWith("white_")) return false;
    String family=in.substring(colon+7);
    String ns=in.substring(0,colon+1);
    if (!out.startsWith(ns)) return false;
    String path=out.substring(colon+1);
    for(String shade:new String[]{"orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"})
      if (path.equals(shade+"_"+family)) return true;
    return false;
  }
'''
s=s.replace('  public static boolean marked(ItemStack item) {',insert+'  public static boolean marked(ItemStack item) {')
p.write_text(s)
p=root/"RgbCreateBridge.java"
s=p.read_text()
s=s.replace('if (outputs.size() != 1 || !RgbRules.marked(outputs.get(0))) return true;','if (outputs.size() != 1) return true;')
s=s.replace('if (source.isEmpty() || pigment.isEmpty()) return false;','if (!RgbRules.marked(outputs.get(0)) && !RgbRules.matchesLegacyColorRecipe(source, outputs.get(0))) return true;\n    if (source.isEmpty() || pigment.isEmpty()) return false;')
p.write_text(s)
print("Verified source adjustments")
