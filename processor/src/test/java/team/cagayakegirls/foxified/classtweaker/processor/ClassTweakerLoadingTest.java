package team.cagayakegirls.foxified.classtweaker.processor;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import team.cagayakegirls.foxified.classtweaker.processor.utils.ASMHelper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 ClassTweaker 文件的加载和解析功能
 */
class ClassTweakerLoadingTest {

    private ClassTweaker classTweaker;
    private ClassTweakerReader reader;

    @BeforeEach
    void setUp() {
        classTweaker = ClassTweaker.newInstance();
        reader = ClassTweakerReader.create(classTweaker);
    }

    @Test
    void testLoadClassTweakerFile() throws IOException {
        // 从测试资源加载 .classtweaker 文件
        byte[] content = loadResource("test.classtweaker");
        assertNotNull(content, "Should be able to load test.classtweaker");

        // 解析文件
        reader.read(content);

        // 验证解析成功
        assertFalse(classTweaker.getTargets().isEmpty(), "Should have targets after loading");
    }

    @Test
    void testClassTweakerFileFormat() throws IOException {
        byte[] content = loadResource("test.classtweaker");
        String text = new String(content, StandardCharsets.UTF_8);

        // 验证文件格式
        assertTrue(text.startsWith("classTweaker\tv1\tofficial"), "File should start with classTweaker header");
        assertTrue(text.contains("accessible field"), "File should contain accessible field rule");
        assertTrue(text.contains("accessible method"), "File should contain accessible method rule");
    }

    @Test
    void testClassTweakerTargets() throws IOException {
        byte[] content = loadResource("test.classtweaker");
        reader.read(content);

        // 验证目标类被正确识别
        var targets = classTweaker.getTargets();
        assertFalse(targets.isEmpty(), "Targets should not be empty");

        // 验证包含预期的目标类
        boolean containsServerCommonPacketListenerImpl = targets.stream()
                .anyMatch(t -> t.contains("ServerCommonPacketListenerImpl"));
        assertTrue(containsServerCommonPacketListenerImpl,
                "Should contain ServerCommonPacketListenerImpl as target");
    }

    @Test
    void testClassTweakerTransformation() throws IOException {
        // 加载 ClassTweaker 配置
        byte[] content = loadResource("test.classtweaker");
        reader.read(content);

        // 创建一个模拟的类节点
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "net/minecraft/server/network/ServerCommonPacketListenerImpl";
        classNode.superName = "java/lang/Object";

        // 序列化并重新解析
        byte[] original = ASMHelper.nodeToBytes(classNode);
        ASMHelper.cleanNode(classNode);

        // 使用 ClassTweaker 的 visitor 进行转换
        ClassVisitor visitor = classTweaker.createClassVisitor(Opcodes.ASM9, classNode, null);
        new ClassReader(original).accept(visitor, 0);

        // 验证转换后的类节点
        assertEquals("net/minecraft/server/network/ServerCommonPacketListenerImpl", classNode.name);
    }

    @Test
    void testLoadMultipleClassTweakerFiles() throws IOException {
        // 加载第一个文件
        byte[] content1 = loadResource("test.classtweaker");
        reader.read(content1);
        int targetsAfterFirst = classTweaker.getTargets().size();

        // 创建第二个 ClassTweaker 配置（模拟）
        ClassTweaker classTweaker2 = ClassTweaker.newInstance();
        ClassTweakerReader reader2 = ClassTweakerReader.create(classTweaker2);
        byte[] content2 = loadResource("test.classtweaker");
        reader2.read(content2);

        // 验证两个配置都能正常加载
        assertFalse(classTweaker.getTargets().isEmpty(), "First config should have targets");
        assertFalse(classTweaker2.getTargets().isEmpty(), "Second config should have targets");
    }

    @Test
    void testModsTomlContainsClassTweakerConfig() throws IOException {
        byte[] tomlContent = loadResource("META-INF/neoforge.mods.toml");
        String toml = new String(tomlContent, StandardCharsets.UTF_8);

        // 验证 TOML 文件包含 ClassTweaker 配置
        assertTrue(toml.contains("[[foxified.classtweaker]]"), "TOML should contain foxified.classtweaker section");
        assertTrue(toml.contains("file = \"test.classtweaker\""), "TOML should contain file reference");
    }

    @Test
    void testParseModsTomlForClassTweakerPaths() throws IOException {
        byte[] tomlContent = loadResource("META-INF/neoforge.mods.toml");
        String toml = new String(tomlContent, StandardCharsets.UTF_8);

        // 解析 TOML 获取 ClassTweaker 文件路径
        String[] lines = toml.split("\\R");
        boolean foundClassTweakerSection = false;
        String filePath = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("[[foxified.classtweaker]]")) {
                foundClassTweakerSection = true;
                continue;
            }
            if (foundClassTweakerSection && trimmed.startsWith("file")) {
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex >= 0) {
                    filePath = trimmed.substring(equalsIndex + 1).trim().replace("\"", "");
                }
                break;
            }
        }

        assertTrue(foundClassTweakerSection, "Should find foxified.classtweaker section");
        assertEquals("test.classtweaker", filePath, "Should parse correct file path");
    }

    /**
     * 从测试资源加载文件
     */
    private byte[] loadResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                fail("Resource not found: " + path);
                return null;
            }
            return is.readAllBytes();
        }
    }
}
