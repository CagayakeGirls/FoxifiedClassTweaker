package team.cagayakegirls.foxified.classtweaker.processor;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import team.cagayakegirls.foxified.classtweaker.processor.utils.ASMHelper;
import team.cagayakegirls.foxified.classtweaker.processor.utils.ClassTweakerLoader;

import java.util.Set;

public final class ClassTweakerProcessor implements ClassProcessor {
	private static final ProcessorName NAME = new ProcessorName("foxified_classtweaker", "processor");

	private final ClassTweaker classTweaker;
	private final Set<String> targets;

	private ClassTweakerProcessor(ClassTweaker classTweaker, Set<String> targets) {
		this.classTweaker = classTweaker;
		this.targets = Set.copyOf(targets);
	}

	@Nullable
	public static ClassTweakerProcessor create() {
		ClassTweaker classTweaker = ClassTweaker.newInstance();
		ClassTweakerReader reader = ClassTweakerReader.create(classTweaker);
		int loadedCount = ClassTweakerLoader.loadFiles(reader);

		if (loadedCount == 0) {
			Contexts.LOGGER.info("No 'foxified.classtweaker' entries found in NeoForge mod metadata");
			return null;
		}

		Set<String> targets = classTweaker.getTargets();
		Contexts.LOGGER.info("Loaded {} ClassTweaker file(s), tracking {} target class(es)", loadedCount, targets.size());
		return new ClassTweakerProcessor(classTweaker, targets);
	}

	@Override
	public ProcessorName name() {
		return NAME;
	}

	@Override
	public Set<ProcessorName> runsAfter() {
		// Run after NeoForge's coremods (field_to_getter, etc.) to avoid conflicts
		return Set.of(
				new ProcessorName("neoforge.coremods", "field_to_getter.net.minecraft.world.level.biome.biome"),
				new ProcessorName("neoforge.coremods", "field_to_getter.net.minecraft.world.level.block.flowerpotblock"),
				new ProcessorName("neoforge.coremods", "field_to_getter.net.minecraft.world.level.levelgen.structure.structure")
		);
	}

	@Override
	public OrderingHint orderingHint() {
		return OrderingHint.LATE;
	}

	@Override
	public boolean handlesClass(SelectionContext context) {
		return !context.empty() && targets.contains(context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		try {
			byte[] original = ASMHelper.nodeToBytes(context.node());
			ASMHelper.cleanNode(context.node());
			ClassVisitor visitor = classTweaker.createClassVisitor(Opcodes.ASM9, context.node(), null);
			ASMHelper.getClassReader(original).accept(visitor, 0);
			context.audit("foxified_class_tweaker", context.type().getInternalName());
			return ComputeFlags.SIMPLE_REWRITE;
		} catch (Exception e) {
			Contexts.LOGGER.error("Failed to transform {}", context.type().getClassName(), e);
			return ComputeFlags.NO_REWRITE;
		}
	}
}
