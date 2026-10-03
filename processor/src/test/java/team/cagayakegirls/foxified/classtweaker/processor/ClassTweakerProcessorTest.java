package team.cagayakegirls.foxified.classtweaker.processor;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import team.cagayakegirls.foxified.classtweaker.processor.utils.ASMHelper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClassTweakerProcessorTest {

    @Test
    void testProcessorName() throws Exception {
        var field = ClassTweakerProcessor.class.getDeclaredField("NAME");
        field.setAccessible(true);
        ProcessorName name = (ProcessorName) field.get(null);

        assertEquals("foxified_classtweaker", name.namespace());
        assertEquals("processor", name.path());
        assertEquals("foxified_classtweaker:processor", name.toString());
    }

    @Test
    void testRunsAfterContainsFieldToGetter() throws Exception {
        ClassTweakerProcessor processor = createTestProcessor();
        Set<ProcessorName> runsAfter = processor.runsAfter();

        // 验证包含 field_to_getter 相关处理器
        ProcessorName biome = new ProcessorName("neoforge.coremods", "field_to_getter.net.minecraft.world.level.biome.biome");
        ProcessorName flowerpot = new ProcessorName("neoforge.coremods", "field_to_getter.net.minecraft.world.level.block.flowerpotblock");
        ProcessorName structure = new ProcessorName("neoforge.coremods", "field_to_getter.net.minecraft.world.level.levelgen.structure.structure");

        assertTrue(runsAfter.contains(biome), "runsAfter should contain biome field_to_getter");
        assertTrue(runsAfter.contains(flowerpot), "runsAfter should contain flowerpot field_to_getter");
        assertTrue(runsAfter.contains(structure), "runsAfter should contain structure field_to_getter");
    }

    @Test
    void testOrderingHintIsLate() throws Exception {
        ClassTweakerProcessor processor = createTestProcessor();
        assertEquals(ClassProcessor.OrderingHint.LATE, processor.orderingHint());
    }

    @Test
    void testNodeToBytes() {
        ClassNode classNode = createTestClassNode();

        byte[] bytes = ASMHelper.nodeToBytes(classNode);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void testNodeToBytesRecomputesMaxsForFrames() {
        // Regression test: Mixin-generated methods can carry stack map frames while
        // maxStack/maxLocals are still 0 (frames are computed after generation). With
        // ClassWriter(0) the emitted StackMapTable is unreadable, so ClassReader throws
        // ArrayIndexOutOfBoundsException and the target class is corrupted.
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "TestFrames";
        classNode.superName = "java/lang/Object";

        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "(Ljava/lang/Object;)I", null, null);
        LabelNode elseLabel = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, elseLabel));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(elseLabel);
        method.instructions.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[] {"java/lang/Object"}, 0, null));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        // maxStack and maxLocals are intentionally left at 0.
        classNode.methods.add(method);

        byte[] bytes = ASMHelper.nodeToBytes(classNode);

        assertDoesNotThrow(() -> new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions);
            }
        }, 0));
    }

    @Test
    void testCleanNode() {
        ClassNode classNode = createTestClassNode();

        // 验证初始状态
        assertEquals(Opcodes.V21, classNode.version);
        assertEquals(Opcodes.ACC_PUBLIC, classNode.access);
        assertEquals("TestClass", classNode.name);
        assertFalse(classNode.fields.isEmpty());

        // 清空节点
        ASMHelper.cleanNode(classNode);

        // 验证清空后的状态
        assertEquals(0, classNode.version);
        assertEquals(0, classNode.access);
        assertNull(classNode.name);
        assertNull(classNode.superName);
        assertNull(classNode.sourceFile);
        assertTrue(classNode.fields.isEmpty());
        assertTrue(classNode.methods.isEmpty());
        assertTrue(classNode.interfaces.isEmpty());
        assertTrue(classNode.innerClasses.isEmpty());
    }

    @Test
    void testCleanNodePreservesFieldStructure() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "TestClass";
        classNode.superName = "java/lang/Object";

        // 添加字段
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "privateField", "I", null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "publicField", "Ljava/lang/String;", null, null));

        // 序列化
        byte[] original = ASMHelper.nodeToBytes(classNode);

        // 清空并重新解析
        ASMHelper.cleanNode(classNode);
        ASMHelper.getClassReader(original).accept(classNode, 0);

        // 验证字段被恢复
        assertEquals(2, classNode.fields.size());
        assertEquals("privateField", classNode.fields.get(0).name);
        assertEquals("publicField", classNode.fields.get(1).name);
    }

    @Test
    void testHandlesClassWithTarget() throws Exception {
        ClassTweakerProcessor processor = createTestProcessor();

        // 创建一个模拟的 SelectionContext
        // 由于 SelectionContext 是 record，我们需要使用反射
        var contextClass = Class.forName("net.neoforged.neoforgespi.transformation.ClassProcessor$SelectionContext");
        var constructor = contextClass.getDeclaredConstructor(org.objectweb.asm.Type.class, boolean.class);
        constructor.setAccessible(true);

        // 创建一个目标类的 context
        org.objectweb.asm.Type type = org.objectweb.asm.Type.getObjectType("TestClass");
        Object context = constructor.newInstance(type, false);

        // 调用 handlesClass
        boolean handles = processor.handlesClass((ClassProcessor.SelectionContext) context);

        // 验证结果（取决于 targets 是否包含 TestClass）
        assertFalse(handles, "Should not handle class not in targets");
    }

    @Test
    void testHandlesClassWithEmptyContext() throws Exception {
        ClassTweakerProcessor processor = createTestProcessor();

        // 创建一个空的 SelectionContext
        var contextClass = Class.forName("net.neoforged.neoforgespi.transformation.ClassProcessor$SelectionContext");
        var constructor = contextClass.getDeclaredConstructor(org.objectweb.asm.Type.class, boolean.class);
        constructor.setAccessible(true);

        org.objectweb.asm.Type type = org.objectweb.asm.Type.getObjectType("TestClass");
        Object context = constructor.newInstance(type, true);

        // 调用 handlesClass
        boolean handles = processor.handlesClass((ClassProcessor.SelectionContext) context);

        // 验证空 context 返回 false
        assertFalse(handles, "Should not handle empty context");
    }

    /**
     * 创建测试用的 ClassNode
     */
    private ClassNode createTestClassNode() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "TestClass";
        classNode.superName = "java/lang/Object";
        classNode.sourceFile = "TestClass.java";
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "field1", "I", null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "field2", "Ljava/lang/String;", null, null));
        return classNode;
    }

    /**
     * 创建测试用的 ClassTweakerProcessor 实例
     */
    private ClassTweakerProcessor createTestProcessor() throws Exception {
        var constructor = ClassTweakerProcessor.class.getDeclaredConstructor(
                net.fabricmc.classtweaker.api.ClassTweaker.class, Set.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, Set.of());
    }
}
