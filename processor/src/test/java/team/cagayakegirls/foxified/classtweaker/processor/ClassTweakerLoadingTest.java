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
        // Load the .classtweaker file from the test resources
        byte[] content = loadResource("test.classtweaker");
        assertNotNull(content, "Should be able to load test.classtweaker");

        // Parse File
        reader.read(content);

        // Verification and parsing were successful
        assertFalse(classTweaker.getTargets().isEmpty(), "Should have targets after loading");
    }

    @Test
    void testClassTweakerFileFormat() throws IOException {
        byte[] content = loadResource("test.classtweaker");
        String text = new String(content, StandardCharsets.UTF_8);

        // Verify File Format
        assertTrue(text.startsWith("classTweaker\tv1\tofficial"), "File should start with classTweaker header");
        assertTrue(text.contains("accessible field"), "File should contain accessible field rule");
        assertTrue(text.contains("accessible method"), "File should contain accessible method rule");
    }

    @Test
    void testClassTweakerTargets() throws IOException {
        byte[] content = loadResource("test.classtweaker");
        reader.read(content);

        // Verify that the target class is correctly identified
        var targets = classTweaker.getTargets();
        assertFalse(targets.isEmpty(), "Targets should not be empty");

        // Verify that it contains the expected target class
        boolean containsServerCommonPacketListenerImpl = targets.stream()
                .anyMatch(t -> t.contains("ServerCommonPacketListenerImpl"));
        assertTrue(containsServerCommonPacketListenerImpl,
                "Should contain ServerCommonPacketListenerImpl as target");
    }

    @Test
    void testClassTweakerTransformation() throws IOException {
        // Load the ClassTweaker configuration
        byte[] content = loadResource("test.classtweaker");
        reader.read(content);

        // Create a simulated class node
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "net/minecraft/server/network/ServerCommonPacketListenerImpl";
        classNode.superName = "java/lang/Object";

        // Serialization and deserialization
        byte[] original = ASMHelper.nodeToBytes(classNode);
        ASMHelper.cleanNode(classNode);

        // Performing transformations using ClassTweaker's visitor
        ClassVisitor visitor = classTweaker.createClassVisitor(Opcodes.ASM9, classNode, null);
        new ClassReader(original).accept(visitor, 0);

        // Validate the converted class nodes
        assertEquals("net/minecraft/server/network/ServerCommonPacketListenerImpl", classNode.name);
    }

    @Test
    void testLoadMultipleClassTweakerFiles() throws IOException {
        // Load the first file
        byte[] content1 = loadResource("test.classtweaker");
        reader.read(content1);
        int targetsAfterFirst = classTweaker.getTargets().size();

        // Create a second ClassTweaker configuration (simulation)
        ClassTweaker classTweaker2 = ClassTweaker.newInstance();
        ClassTweakerReader reader2 = ClassTweakerReader.create(classTweaker2);
        byte[] content2 = loadResource("test.classtweaker");
        reader2.read(content2);

        // Verify that both configurations load correctly
        assertFalse(classTweaker.getTargets().isEmpty(), "First config should have targets");
        assertFalse(classTweaker2.getTargets().isEmpty(), "Second config should have targets");
    }

    @Test
    void testModsTomlContainsClassTweakerConfig() throws IOException {
        byte[] tomlContent = loadResource("META-INF/neoforge.mods.toml");
        String toml = new String(tomlContent, StandardCharsets.UTF_8);

        // Verify that the TOML file contains the ClassTweaker configuration
        assertTrue(toml.contains("[[foxified.classtweaker]]"), "TOML should contain foxified.classtweaker section");
        assertTrue(toml.contains("file = \"test.classtweaker\""), "TOML should contain file reference");
    }

    @Test
    void testParseModsTomlForClassTweakerPaths() throws IOException {
        byte[] tomlContent = loadResource("META-INF/neoforge.mods.toml");
        String toml = new String(tomlContent, StandardCharsets.UTF_8);

        // Parsing TOML to Retrieve the ClassTweaker File Path
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
     * Loading Files from Test Resources
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
