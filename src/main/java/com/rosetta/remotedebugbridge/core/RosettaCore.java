/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.packs.PackType
 *  net.minecraft.server.packs.repository.Pack
 *  net.minecraft.server.packs.repository.Pack$Position
 *  net.minecraft.server.packs.repository.PackSource
 *  net.minecraftforge.event.AddPackFindersEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 *  net.minecraftforge.fml.loading.FMLPaths
 */
package com.rosetta.remotedebugbridge.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge;
import com.rosetta.remotedebugbridge.core.ScriptType;
import com.rosetta.remotedebugbridge.script.JavaScriptLoader;
import com.rosetta.remotedebugbridge.logging.RosettaLogger;
import com.rosetta.remotedebugbridge.logging.ScriptErrorCollector;
import com.rosetta.remotedebugbridge.resources.RosettaResourcePack;

@Mod.EventBusSubscriber(modid="rosetta_remote_debug_bridge", bus=Mod.EventBusSubscriber.Bus.MOD)
public class RosettaCore {
    private static final String ROSETTA_FOLDER = "RosettaRemoteDebugBridge";
    private final Path rootPath;
    private final Path serverPath;
    private final Path clientPath;
    private final Path startupPath;
    private final Path assetsPath;
    private final Path dataPath;
    private final Path coreModPath;
    public boolean mixinsLoaded;
    private final Map<ScriptType, RosettaLogger> loggers = new HashMap<ScriptType, RosettaLogger>();
    private final Map<ScriptType, JavaScriptLoader> loaders = new HashMap<ScriptType, JavaScriptLoader>();
    private final Map<ScriptType, Boolean> loadedFlags = new HashMap<ScriptType, Boolean>();

    public RosettaCore() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        this.rootPath = gameDir.resolve(ROSETTA_FOLDER);
        this.serverPath = this.rootPath.resolve("server");
        this.clientPath = this.rootPath.resolve("client");
        this.startupPath = this.rootPath.resolve("startup");
        this.assetsPath = this.rootPath.resolve("assets");
        this.dataPath = this.rootPath.resolve("data");
        this.coreModPath = this.rootPath.resolve("coremod");
        for (ScriptType type : ScriptType.values()) {
            this.loggers.put(type, new RosettaLogger(type));
        }
        this.initializeFolders();
        for (ScriptType type : ScriptType.values()) {
            this.loaders.put(type, new JavaScriptLoader(type));
            this.loadedFlags.put(type, false);
        }
        try {
            com.rosetta.remotedebugbridge.script.util.NetworkUtils.init(RosettaRemoteDebugBridge.MOD_ID);
        }
        catch (Throwable t) {
            RosettaRemoteDebugBridge.LOGGER.warn("RosettaRemoteDebugBridge network channel initialization failed: {}", (Object)t.toString());
        }
        RosettaRemoteDebugBridge.LOGGER.info("RosettaRemoteDebugBridge Core initialized at: {}", (Object)this.rootPath);
    }

    private void initializeFolders() {
        try {
            Files.createDirectories(this.serverPath, new FileAttribute[0]);
            Files.createDirectories(this.clientPath, new FileAttribute[0]);
            Files.createDirectories(this.startupPath, new FileAttribute[0]);
            Files.createDirectories(this.dataPath, new FileAttribute[0]);
            Files.createDirectories(this.assetsPath, new FileAttribute[0]);
            this.createExampleFiles();
            RosettaRemoteDebugBridge.LOGGER.info("RosettaRemoteDebugBridge folder structure ready at: {}", (Object)this.rootPath);
        }
        catch (IOException e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to create RosettaRemoteDebugBridge folder structure", (Throwable)e);
        }
    }

    private void createExampleFiles() {
        try {
            Path readme;
            Path exampleFile = this.startupPath.resolve("Example.java");
            if (!Files.exists(exampleFile, new LinkOption[0])) {
                String code = "package rosetta.startup;\n\n/**\n * \u793a\u4f8b\u542f\u52a8\u811a\u672c\uff0c\u6e38\u620f\u521d\u59cb\u5316\u65f6\u6267\u884c\u4e00\u6b21\u3002\n */\npublic class Example {\n    public static void init() {\n        System.out.println(\"=================================\");\n        System.out.println(\"Hello from RosettaRemoteDebugBridge startup script!\");\n        System.out.println(\"=================================\");\n    }\n}\n";
                Files.writeString(exampleFile, (CharSequence)code, StandardCharsets.UTF_8);
            }
            if (!Files.exists(readme = this.rootPath.resolve("README.txt"), new LinkOption[0])) {
                String txt = "#######################################################################\n#            RosettaRemoteDebugBridge - Dynamic Java Script & Modding System          #\n#######################################################################\n\nOVERVIEW\n========\nRosettaRemoteDebugBridge lets you write and hot-reload Java code at Minecraft runtime\nwithout restarting the game. It also provides a Mixin system for\nmethod injection and a CoreMod / ASM transformer pipeline for low-level\nbytecode patching of any game class.\n\nFOLDER LAYOUT\n=============\n  RosettaRemoteDebugBridge/\n  \u251c\u2500\u2500 startup/    Runs once when the game initialises\n  \u251c\u2500\u2500 server/     Runs each time the server starts\n  \u251c\u2500\u2500 client/     Runs during client setup\n  \u251c\u2500\u2500 mixins/     Mixin classes that patch game classes at runtime\n  \u251c\u2500\u2500 coremod/    CoreMod plugins and ASM transformer classes\n  \u251c\u2500\u2500 assets/     Custom resource pack (textures, models, lang, sounds)\n  \u251c\u2500\u2500 data/       Custom data pack (recipes, loot tables, tags)\n  \u2514\u2500\u2500 README.txt  This file\n\nSCRIPT BASICS\n=============\nEvery script class must declare a public static init() method.\nThe package must match the folder type:\n\n  startup  \u2192  package rosetta.startup;\n  server   \u2192  package rosetta.server;\n  client   \u2192  package rosetta.client;\n\nThe init() method may optionally accept a FMLJavaModLoadingContext\nparameter, which is passed in automatically when present:\n\n  public static void init() { ... }\n  public static void init(FMLJavaModLoadingContext ctx) { ... }\n\nExample (startup/Hello.java):\n\n  package rosetta.startup;\n  import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;\n\n  public class Hello {\n      public static void init(FMLJavaModLoadingContext ctx) {\n          System.out.println(\"Hello from RosettaRemoteDebugBridge!\");\n      }\n  }\n\nMIXIN SYSTEM\n============\nPlace Mixin source files in the mixins/ folder.\nA Mixin class must:\n  1. Implement  net.rain.api.mixin.IMixin\n  2. Override   getTargetClass() with the fully-qualified target class name\n  3. Use @Inject (with @Inject.At) to mark the injection point\n  4. Accept CallbackInfo as the last parameter of any @Inject method\n\nOptional IMixin members:\n  getPriority()  \u2014 injection priority (default 1000, higher = earlier)\n  isEnabled()    \u2014 return false to disable this Mixin entirely\n\nSupported @Inject.At values:\n  \"HEAD\"   \u2014 Before the first instruction of the method\n  \"RETURN\" \u2014 Before every return statement\n  \"TAIL\"   \u2014 Before the final return (or throw)\n\nUse @Unique on helper fields/methods to prevent name collisions.\n\nMixins are compiled at startup and injected lazily when the target\nclass is first loaded. Game classes (e.g. DamageSource) can be\nreferenced freely because the compiler classpath includes all game JARs.\n\nExample (mixins/MyMixin.java):\n\n  package rosetta.mixins;\n  import net.rain.api.mixin.IMixin;\n  import net.rain.api.mixin.annotation.Inject;\n  import net.rain.api.mixin.callback.CallbackInfo;\n\n  public class MyMixin implements IMixin {\n      @Override\n      public String getTargetClass() {\n          return \"net.minecraft.client.Minecraft\";\n      }\n\n      @Inject(method = \"run\", at = @Inject.At(value = \"HEAD\"))\n      public void onRun(CallbackInfo ci) {\n          System.out.println(\"Minecraft.run() called!\");\n      }\n  }\n\nCOREMOD SYSTEM\n==============\nCoreMods allow ASM bytecode transformation of any class before it is\nloaded by the JVM. Place your plugin and transformer sources in\ncoremod/, then declare them in coremod/coremod.json:\n\n  {\n      \"plugins\": \"rosetta_remote_debug_bridge.coremod.MyCoreModLoadingPlugin\"\n  }\n\nThe plugin class must implement ICoreModLoadingPlugin, be annotated\nwith @ICoreModLoadingPlugin.Name, and return transformer class names\nfrom getASMTransformerClass().\n\nEach transformer implements ICoreClassTransformer:\n  ClassNode transform(String className, ClassNode basicClass)\nReturn the ClassNode unchanged if no modification is needed.\n\nExample (coremod/MyCoreModLoadingPlugin.java):\n\n  package rosetta_remote_debug_bridge.coremod;\n  import net.rain.api.coremod.ICoreModLoadingPlugin;\n  import org.objectweb.asm.tree.ClassNode;\n  import java.util.Map;\n\n  @ICoreModLoadingPlugin.Name(\"MyCoremod\")\n  public class MyCoreModLoadingPlugin implements ICoreModLoadingPlugin {\n      @Override\n      public String[] getASMTransformerClass() {\n          return new String[]{ \"rosetta_remote_debug_bridge.coremod.MyTransformer\" };\n      }\n      @Override\n      public void injectData(Map<String, Object> data) { }\n  }\n\nExample (coremod/MyTransformer.java):\n\n  package rosetta_remote_debug_bridge.coremod;\n  import net.rain.api.coremod.ICoreClassTransformer;\n\n  public class MyTransformer implements ICoreClassTransformer {\n      @Override\n      public ClassNode transform(String className, ClassNode cn) {\n          // Modify cn with ASM here, then return them\n          return cn;\n      }\n  }\n\nCOMMANDS\n========\n  /java reload [startup|server|client]  Hot-reload scripts without restarting\n  /java hand getId                      Print held item registry ID\n  /java hand getClass                   Print held item Java class name\n  /java errors [startup|server|client]  Show script error summary in chat\n  /j <...>                              Alias for /java\n\nLOGS\n====\n  logs/Java/startup.log\n  logs/Java/server.log\n  logs/Java/client.log\n\nEach script type writes to its own log file for easy error isolation.\nThe main game log also receives summary entries.\n\nTIPS\n====\n  - Mixins and CoreMods load at startup; changes require a full restart.\n  - Scripts (startup/server/client) support hot-reload via /java reload.\n  - If no system Java compiler is found, the bundled Eclipse JDT is used.\n  - Mixin method signatures must be compatible with the target method.\n  - Use @Unique on helpers to avoid name collisions with target classes.\n\n\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n\n#######################################################################\n#          RosettaRemoteDebugBridge - \u52a8\u6001 Java \u811a\u672c\u4e0e Mod \u7cfb\u7edf                   #\n#######################################################################\n\n\u6982\u8ff0\n====\nRosettaRemoteDebugBridge \u5141\u8bb8\u4f60\u5728 Minecraft \u8fd0\u884c\u65f6\u7f16\u5199\u5e76\u70ed\u91cd\u8f7d Java \u4ee3\u7801\uff0c\u65e0\u9700\u91cd\u542f\u6e38\u620f\u3002\n\u540c\u65f6\u63d0\u4f9b Mixin \u65b9\u6cd5\u6ce8\u5165\u7cfb\u7edf\uff0c\u4ee5\u53ca CoreMod / ASM \u8f6c\u6362\u5668\u6d41\u6c34\u7ebf\uff0c\n\u53ef\u5728 JVM \u52a0\u8f7d\u7c7b\u4e4b\u524d\u5bf9\u4efb\u610f\u6e38\u620f\u7c7b\u8fdb\u884c\u5e95\u5c42\u5b57\u8282\u7801\u4fee\u8865\u3002\n\n\u76ee\u5f55\u7ed3\u6784\n========\n  RosettaRemoteDebugBridge/\n  \u251c\u2500\u2500 startup/    \u6e38\u620f\u521d\u59cb\u5316\u65f6\u6267\u884c\u4e00\u6b21\n  \u251c\u2500\u2500 server/     \u6bcf\u6b21\u670d\u52a1\u7aef\u542f\u52a8\u65f6\u6267\u884c\n  \u251c\u2500\u2500 client/     \u5ba2\u6237\u7aef\u521d\u59cb\u5316\u65f6\u6267\u884c\n  \u251c\u2500\u2500 mixins/     \u8fd0\u884c\u65f6\u4fee\u6539\u6e38\u620f\u7c7b\u7684 Mixin \u6e90\u6587\u4ef6\n  \u251c\u2500\u2500 coremod/    CoreMod \u63d2\u4ef6\u4e0e ASM \u5b57\u8282\u7801\u8f6c\u6362\u5668\n  \u251c\u2500\u2500 assets/     \u81ea\u5b9a\u4e49\u8d44\u6e90\u5305\uff08\u8d34\u56fe\u3001\u6a21\u578b\u3001\u8bed\u8a00\u6587\u4ef6\u3001\u97f3\u6548\u7b49\uff09\n  \u251c\u2500\u2500 data/       \u81ea\u5b9a\u4e49\u6570\u636e\u5305\uff08\u914d\u65b9\u3001\u6218\u5229\u54c1\u8868\u3001\u6807\u7b7e\u7b49\uff09\n  \u2514\u2500\u2500 README.txt  \u672c\u6587\u4ef6\n\n\u811a\u672c\u57fa\u7840\n========\n\u6bcf\u4e2a\u811a\u672c\u7c7b\u5fc5\u987b\u5305\u542b public static void init() \u65b9\u6cd5\u3002\n\u5305\u540d\u5fc5\u987b\u4e0e\u6240\u5728\u76ee\u5f55\u7c7b\u578b\u5bf9\u5e94\uff1a\n\n  startup  \u2192  package rosetta.startup;\n  server   \u2192  package rosetta.server;\n  client   \u2192  package rosetta.client;\n\ninit() \u65b9\u6cd5\u53ef\u9009\u5730\u63a5\u53d7 FMLJavaModLoadingContext \u53c2\u6570\uff0c\u5b58\u5728\u65f6\u4f1a\u81ea\u52a8\u4f20\u5165\uff1a\n\n  public static void init() { ... }\n  public static void init(FMLJavaModLoadingContext ctx) { ... }\n\n\u793a\u4f8b\uff08startup/Hello.java\uff09\uff1a\n\n  package rosetta.startup;\n  import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;\n\n  public class Hello {\n      public static void init(FMLJavaModLoadingContext ctx) {\n          System.out.println(\"Hello from RosettaRemoteDebugBridge!\");\n      }\n  }\n\nMixin \u7cfb\u7edf\n==========\n\u5c06 Mixin \u6e90\u6587\u4ef6\u653e\u5165 mixins/ \u76ee\u5f55\u3002\nMixin \u7c7b\u5fc5\u987b\u6ee1\u8db3\uff1a\n  1. \u5b9e\u73b0  net.rain.api.mixin.IMixin \u63a5\u53e3\n  2. \u91cd\u5199  getTargetClass()\uff0c\u8fd4\u56de\u76ee\u6807\u7c7b\u7684\u5168\u9650\u5b9a\u540d\n  3. @Inject \u65b9\u6cd5\u7684\u6700\u540e\u4e00\u4e2a\u53c2\u6570\u5fc5\u987b\u662f CallbackInfo\n\nIMixin \u53ef\u9009\u6210\u5458\uff1a\n  getPriority()  \u2014 \u6ce8\u5165\u4f18\u5148\u7ea7\uff08\u9ed8\u8ba4 1000\uff0c\u503c\u8d8a\u5927\u8d8a\u5148\u6ce8\u5165\uff09\n  isEnabled()    \u2014 \u8fd4\u56de false \u53ef\u5b8c\u5168\u7981\u7528\u8be5 Mixin\n\n@Inject.At \u652f\u6301\u7684\u6ce8\u5165\u70b9\uff1a\n  \"HEAD\"   \u2014 \u65b9\u6cd5\u7b2c\u4e00\u6761\u6307\u4ee4\u4e4b\u524d\n  \"RETURN\" \u2014 \u6bcf\u4e2a return \u8bed\u53e5\u4e4b\u524d\n  \"TAIL\"   \u2014 \u6700\u540e\u4e00\u4e2a return\uff08\u6216 throw\uff09\u4e4b\u524d\n  \u8fd8\u6709\"FIELD\"\uff0c\"INVOKE\"\u7b49\n\n\u5728\u8f85\u52a9\u5b57\u6bb5/\u65b9\u6cd5\u4e0a\u4f7f\u7528 @Unique\uff0c\u9632\u6b62\u4e0e\u76ee\u6807\u7c7b\u6210\u5458\u547d\u540d\u51b2\u7a81\u3002\n\nMixin \u5728\u542f\u52a8\u9636\u6bb5\u7f16\u8bd1\u5e76\u7f13\u5b58\u5b57\u8282\u7801\uff0c\u76ee\u6807\u7c7b\u9996\u6b21\u88ab\u52a0\u8f7d\u65f6\u624d\u771f\u6b63\u6ce8\u5165\uff08\u61d2\u52a0\u8f7d\uff09\u3002\n\u53ef\u4ee5\u76f4\u63a5\u5f15\u7528\u6e38\u620f\u7c7b\uff08\u5982 DamageSource\uff09\uff0c\u7f16\u8bd1\u5668 classpath \u5df2\u5305\u542b\u6240\u6709\u6e38\u620f JAR\u3002\n\u53ef\u4ee5\u76f4\u63a5\u4f7f\u7528mcp\u540d\u53bb\u5339\u914d\u65b9\u6cd5\u540d\u3002\n\n\u793a\u4f8b\uff08mixins/MyMixin.java\uff09\uff1a\n\n  package rosetta.mixins;\n  import net.rain.api.mixin.IMixin;\n  import net.rain.api.mixin.annotation.Inject;\n  import net.rain.api.mixin.callback.CallbackInfo;\n\n  public class MyMixin implements IMixin {\n      @Override\n      public String getTargetClass() {\n          return \"net.minecraft.client.Minecraft\";\n      }\n\n      @Inject(method = \"run\", at = @Inject.At(value = \"HEAD\"))\n      public void onRun(CallbackInfo ci) {\n          System.out.println(\"Minecraft.run() \u88ab\u8c03\u7528\uff01\");\n      }\n  }\n\nCoreMod \u7cfb\u7edf\n============\nCoreMod \u5728 JVM \u52a0\u8f7d\u7c7b\u4e4b\u524d\u5bf9\u4efb\u610f\u7c7b\u8fdb\u884c ASM \u5b57\u8282\u7801\u8f6c\u6362\u3002\n\u5c06\u63d2\u4ef6\u548c\u8f6c\u6362\u5668\u6e90\u6587\u4ef6\u653e\u5165 coremod/ \u76ee\u5f55\uff0c\n\u5e76\u5728 coremod/coremod.json \u4e2d\u58f0\u660e\uff1a\n\n  {\n      \"plugins\": \"rosetta_remote_debug_bridge.coremod.MyCoreModLoadingPlugin\"\n  }\n\n\u63d2\u4ef6\u7c7b\u5fc5\u987b\u5b9e\u73b0 ICoreModLoadingPlugin\uff0c\u6807\u6ce8 @ICoreModLoadingPlugin.Name\uff0c\n\u5e76\u901a\u8fc7 getASMTransformerClass() \u8fd4\u56de\u8f6c\u6362\u5668\u7c7b\u7684\u5168\u9650\u5b9a\u540d\u6570\u7ec4\u3002\n\n\u6bcf\u4e2a\u8f6c\u6362\u5668\u5b9e\u73b0 ICoreClassTransformer\uff1a\n  ClassNode transform(String className, ClassNode basicClass)\n\u82e5\u4e0d\u9700\u8981\u4fee\u6539\u67d0\u4e2a\u7c7b\uff0c\u76f4\u63a5\u8fd4\u56de\u539f\u59cbClassNode\u5373\u53ef\u3002\n\n\u793a\u4f8b\uff08coremod/MyCoreModLoadingPlugin.java\uff09\uff1a\n\n  package rosetta_remote_debug_bridge.coremod;\n  import net.rain.api.coremod.ICoreModLoadingPlugin;\n  import org.objectweb.asm.tree.ClassNode;\n  import java.util.Map;\n\n  @ICoreModLoadingPlugin.Name(\"MyCoremod\")\n  public class MyCoreModLoadingPlugin implements ICoreModLoadingPlugin {\n      @Override\n      public String[] getASMTransformerClass() {\n          return new String[]{ \"rosetta_remote_debug_bridge.coremod.MyTransformer\" };\n      }\n      @Override\n      public void injectData(Map<String, Object> data) { }\n  }\n\n\u793a\u4f8b\uff08coremod/MyTransformer.java\uff09\uff1a\n\n  package rosetta_remote_debug_bridge.coremod;\n  import net.rain.api.coremod.ICoreClassTransformer;\n\n  public class MyTransformer implements ICoreClassTransformer {\n      @Override\n      public ClassNode transform(String className, ClassNode cn) {\n          // \u5728\u6b64\u7528 ASM \u4fee\u6539\u5b57\u8282\u7801\uff0c\u7136\u540e\u8fd4\u56de\n          return cn;\n      }\n  }\n\n\u547d\u4ee4\n====\n  /java reload [startup|server|client]  \u70ed\u91cd\u8f7d\u6307\u5b9a\u7c7b\u578b\u7684\u811a\u672c\uff0c\u65e0\u9700\u91cd\u542f\n  /java hand getId                      \u8f93\u51fa\u5f53\u524d\u624b\u6301\u7269\u54c1\u7684\u6ce8\u518c\u8868 ID\n  /java hand getClass                   \u8f93\u51fa\u5f53\u524d\u624b\u6301\u7269\u54c1\u7684 Java \u7c7b\u540d\n  /java errors [startup|server|client]  \u5728\u804a\u5929\u6846\u4e2d\u663e\u793a\u811a\u672c\u9519\u8bef\u6458\u8981\n  /j <...>                              /java \u7684\u7b80\u5199\u522b\u540d\n\n\u65e5\u5fd7\n====\n  logs/Java/startup.log\n  logs/Java/server.log\n  logs/Java/client.log\n\n\u6bcf\u79cd\u811a\u672c\u7c7b\u578b\u72ec\u7acb\u5199\u5165\u65e5\u5fd7\u6587\u4ef6\uff0c\u4fbf\u4e8e\u9694\u79bb\u5b9a\u4f4d\u9519\u8bef\u3002\n\u4e3b\u6e38\u620f\u65e5\u5fd7\u4e5f\u4f1a\u6536\u5230\u6458\u8981\u6761\u76ee\u3002\n\n\u6ce8\u610f\u4e8b\u9879\n========\n  - Mixin \u548c CoreMod \u5728\u542f\u52a8\u65f6\u52a0\u8f7d\uff0c\u4fee\u6539\u540e\u9700\u8981\u5b8c\u5168\u91cd\u542f\u6e38\u620f\u3002\n  - \u666e\u901a\u811a\u672c\uff08startup/server/client\uff09\u652f\u6301 /java reload \u70ed\u91cd\u8f7d\uff0c\u65e0\u9700\u91cd\u542f\u3002\n  - \u82e5 JRE \u6ca1\u6709\u5185\u7f6e Java \u7f16\u8bd1\u5668\uff0cRosettaRemoteDebugBridge \u4f1a\u81ea\u52a8\u4f7f\u7528\u5185\u5d4c\u7684 Eclipse JDT\u3002\n  - Mixin \u65b9\u6cd5\u7b7e\u540d\u5fc5\u987b\u4e0e\u76ee\u6807\u65b9\u6cd5\u517c\u5bb9\uff0c\u5426\u5219\u6ce8\u5165\u65f6\u4f1a\u629b\u51fa\u5f02\u5e38\u3002\n  - \u5728\u8f85\u52a9\u5b57\u6bb5/\u65b9\u6cd5\u4e0a\u4f7f\u7528 @Unique\uff0c\u9632\u6b62\u4e0e\u76ee\u6807\u7c7b\u6210\u5458\u547d\u540d\u51b2\u7a81\u3002\n";
                Files.writeString(readme, (CharSequence)txt, StandardCharsets.UTF_8);
            }
        }
        catch (IOException e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to create example files", (Throwable)e);
        }
    }

    public void loadScripts(ScriptType type) {
        if (this.loadedFlags.get((Object)type).booleanValue()) {
            RosettaRemoteDebugBridge.LOGGER.debug("Scripts of type {} already loaded, skipping", (Object)type);
            return;
        }
        this.doLoad(type);
        this.loadedFlags.put(type, true);
    }

    public void reload(ScriptType type) {
        RosettaLogger logger = this.loggers.get((Object)type);
        logger.info("==== Reloading {} scripts ====", new Object[]{type});
        ScriptErrorCollector.clear(type);
        JavaScriptLoader previous = this.loaders.get((Object)type);
        if (previous != null && previous.getClassLoader() != null) {
            int removed = RosettaRemoteDebugBridge.EVENT_BUS.unregisterByClassLoader(previous.getClassLoader());
            if (removed > 0) {
                logger.info("Unregistered {} event listener(s) from previous {} script classes", removed, type);
            }
            try {
                int[] bukkitRemoved = com.rosetta.remotedebugbridge.net.BukkitAdapter.cleanupClassLoader(previous.getClassLoader());
                if (bukkitRemoved[0] > 0 || bukkitRemoved[1] > 0) {
                    logger.info("Unregistered {} Bukkit listener(s) and {} command(s) from previous {} script classes",
                            bukkitRemoved[0], bukkitRemoved[1], type);
                }
            }
            catch (Throwable t) {
                logger.warn("Bukkit cleanup failed for {}: {}", type, t.toString());
            }
        }
        this.loadedFlags.put(type, false);
        this.loaders.put(type, new JavaScriptLoader(type));
        this.doLoad(type);
        this.loadedFlags.put(type, true);
    }

    private void doLoad(ScriptType type) {
        RosettaLogger logger = this.loggers.get((Object)type);
        logger.info("Loading {} scripts", new Object[]{type});
        JavaScriptLoader loader = this.loaders.get((Object)type);
        if (loader == null || !loader.isAvailable()) {
            logger.warn("Java compiler not available, skipping {} script loading", new Object[]{type});
            return;
        }
        Path scriptPath = this.getScriptPath(type);
        try {
            if (type == ScriptType.STARTUP && !this.mixinsLoaded) {
                loader.processMixins();
            }
            loader.loadJavaScripts(scriptPath);
            logger.info("Finished loading {} scripts", new Object[]{type});
        }
        catch (Throwable t) {
            logger.error("Failed to load {} scripts: {}", new Object[]{type, t.getMessage(), t});
            ScriptErrorCollector.addFromThrowable(type, type.getName(), t);
        }
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path assetsPath = gameDir.resolve(ROSETTA_FOLDER).resolve("assets");
            Path dataPath = gameDir.resolve(ROSETTA_FOLDER).resolve("data");
            if (event.getPackType() == PackType.CLIENT_RESOURCES && Files.exists(assetsPath, new LinkOption[0])) {
                RosettaRemoteDebugBridge.LOGGER.info("Registering RosettaRemoteDebugBridge assets pack");
                event.addRepositorySource(consumer -> {
                    RosettaResourcePack pack = new RosettaResourcePack(assetsPath, PackType.CLIENT_RESOURCES);
                    consumer.accept(Pack.readMetaAndCreate((String)"rosetta_assets", (Component)Component.literal((String)"RosettaRemoteDebugBridge Assets"), (boolean)true, name -> pack, (PackType)PackType.CLIENT_RESOURCES, (Pack.Position)Pack.Position.TOP, (PackSource)PackSource.DEFAULT));
                });
            }
            if (event.getPackType() == PackType.SERVER_DATA && Files.exists(dataPath, new LinkOption[0])) {
                RosettaRemoteDebugBridge.LOGGER.info("Registering RosettaRemoteDebugBridge data pack");
                event.addRepositorySource(consumer -> {
                    RosettaResourcePack pack = new RosettaResourcePack(dataPath, PackType.SERVER_DATA);
                    consumer.accept(Pack.readMetaAndCreate((String)"rosetta_data", (Component)Component.literal((String)"RosettaRemoteDebugBridge Data"), (boolean)true, name -> pack, (PackType)PackType.SERVER_DATA, (Pack.Position)Pack.Position.TOP, (PackSource)PackSource.DEFAULT));
                });
            }
        }
        catch (Exception e) {
            RosettaRemoteDebugBridge.LOGGER.error("Failed to register resource packs", (Throwable)e);
        }
    }

    public Path getRootPath() {
        return this.rootPath;
    }

    public Path getServerPath() {
        return this.serverPath;
    }

    public Path getClientPath() {
        return this.clientPath;
    }

    public Path getStartupPath() {
        return this.startupPath;
    }

    public Path getAssetsPath() {
        return this.assetsPath;
    }

    public Path getDataPath() {
        return this.dataPath;
    }

    public JavaScriptLoader getLoader(ScriptType type) {
        return this.loaders.get((Object)type);
    }

    public RosettaLogger getLogger(ScriptType type) {
        return this.loggers.get((Object)type);
    }

    private Path getScriptPath(ScriptType type) {
        return switch (type) {
            default -> throw new IncompatibleClassChangeError();
            case SERVER -> this.serverPath;
            case CLIENT -> this.clientPath;
            case STARTUP -> this.startupPath;
        };
    }
}

