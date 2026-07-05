package team.cagayakegirls.foxified.classtweaker.processor.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;

public class ASMHelper {
    public static byte[] nodeToBytes(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public static void cleanNode(ClassNode classNode) {
        classNode.version = 0;
        classNode.access = 0;
        classNode.name = null;
        classNode.signature = null;
        classNode.superName = null;
        classNode.interfaces = new ArrayList<>();
        classNode.sourceFile = null;
        classNode.sourceDebug = null;
        classNode.module = null;
        classNode.outerClass = null;
        classNode.outerMethod = null;
        classNode.outerMethodDesc = null;
        classNode.visibleAnnotations = null;
        classNode.invisibleAnnotations = null;
        classNode.visibleTypeAnnotations = null;
        classNode.invisibleTypeAnnotations = null;
        classNode.attrs = null;
        classNode.innerClasses = new ArrayList<>();
        classNode.nestHostClass = null;
        classNode.nestMembers = null;
        classNode.permittedSubclasses = null;
        classNode.recordComponents = null;
        classNode.fields = new ArrayList<>();
        classNode.methods = new ArrayList<>();
    }

    public static ClassReader getClassReader(byte[] classBytes) {
        return new ClassReader(classBytes);
    }
}
