package team.cagayakegirls.foxified.classtweaker.processor;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassTweakerProcessor implements ClassProcessor {
	private static final ProcessorName NAME = new ProcessorName("foxified_classtweaker", "processor");
	private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]+)\"|'([^']+)'");

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
		int loadedCount = loadFromLoadingModList(reader);

		// Fallback for unusual bootstrap scenarios where FML loader context is unavailable.
		if (loadedCount == 0) {
			loadedCount = loadFromClasspath(reader);
		}

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
	public boolean handlesClass(SelectionContext context) {
		return !context.empty() && targets.contains(context.type().getInternalName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		try {
			byte[] original = toBytes(context.node());
			clearClassNode(context.node());
			ClassVisitor visitor = classTweaker.createClassVisitor(Opcodes.ASM9, context.node(), null);
			new ClassReader(original).accept(visitor, 0);
			context.audit("foxified_class_tweaker", context.type().getInternalName());
			return ComputeFlags.SIMPLE_REWRITE;
		} catch (Exception e) {
			Contexts.LOGGER.error("Failed to transform {}", context.type().getClassName(), e);
			return ComputeFlags.NO_REWRITE;
		}
	}

	private static byte[] toBytes(ClassNode classNode) {
		ClassWriter writer = new ClassWriter(0);
		classNode.accept(writer);
		return writer.toByteArray();
	}

	private static void clearClassNode(ClassNode classNode) {
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

	private static List<URL> enumerateModsTomlResources(ClassLoader classLoader) throws IOException {
		List<URL> urls = new ArrayList<>();
		Enumeration<URL> resources = classLoader.getResources(Contexts.MODS_TOML);
		while (resources.hasMoreElements()) {
			urls.add(resources.nextElement());
		}
		return urls;
	}

	private static List<String> readClassTweakerPaths(byte[] metadataContent) {
		String text = new String(metadataContent, StandardCharsets.UTF_8);
		String[] lines = text.split("\\R");
		List<String> paths = new ArrayList<>();
		boolean inTargetSection = false;

		for (String line : lines) {
			String noComment = stripTomlComment(line).trim();
			if (noComment.isEmpty()) {
				continue;
			}

			if (noComment.startsWith("[[") && noComment.endsWith("]]")) {
				inTargetSection = Contexts.CLASSTWEAKER_HEADER.equals(noComment);
				continue;
			}

			if (!inTargetSection || !noComment.startsWith("file")) {
				continue;
			}

			int equalsIndex = noComment.indexOf('=');
			if (equalsIndex < 0 || equalsIndex >= noComment.length() - 1) {
				continue;
			}

			String rhs = noComment.substring(equalsIndex + 1).trim();
			Matcher matcher = QUOTED_VALUE.matcher(rhs);
			while (matcher.find()) {
				String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
				if (!value.isBlank()) {
					paths.add(normalizePath(value));
				}
			}
		}
		return paths;
	}

	private static int loadFromLoadingModList(ClassTweakerReader reader) {
		FMLLoader loader = FMLLoader.getCurrentOrNull();
		if (loader == null) {
			return 0;
		}

		Set<String> loadedEntries = new HashSet<>();
		int loadedCount = 0;

		for (ModFileInfo modFileInfo : loader.getLoadingModList().getModFiles()) {
			var modFile = modFileInfo.getFile();
			var contents = modFile.getContents();
			try {
				List<? extends IConfigurable> sectionEntries = modFileInfo.getConfigList("foxified", "classtweaker");
				List<String> classTweakerPaths = new ArrayList<>(sectionEntries.size());
				for (var entry : sectionEntries) {
					Optional<String> file = entry.getConfigElement("file");
					if (file.isEmpty() || file.get().isBlank()) {
						Contexts.LOGGER.warn("Invalid 'foxified.classtweaker' entry in '{}': missing file", modFile.getFileName());
						continue;
					}
					classTweakerPaths.add(normalizePath(file.get()));
				}

				for (String path : classTweakerPaths) {
					String dedupe = modFile.getFileName() + "::" + path;
					if (!loadedEntries.add(dedupe)) {
						continue;
					}

					byte[] content = contents.readFile(path);
					if (content == null) {
						Contexts.LOGGER.warn("ClassTweaker file '{}' declared by '{}' was not found", path, modFile.getFileName());
						continue;
					}

					reader.read(content);
					loadedCount++;
					Contexts.LOGGER.debug("Loaded ClassTweaker '{}' from '{}'", path, modFile.getFileName());
				}
			} catch (Exception e) {
				Contexts.LOGGER.warn("Failed to process mod file '{}': {}", modFile.getFileName(), e.toString());
			}
		}

		return loadedCount;
	}

	private static int loadFromClasspath(ClassTweakerReader reader) {
		try {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			List<URL> metadataFiles = enumerateModsTomlResources(classLoader);
			Set<String> loadedEntries = new HashSet<>();
			int loadedCount = 0;

			for (URL metadataUrl : metadataFiles) {
				try (InputStream input = metadataUrl.openStream()) {
					List<String> classTweakerPaths = readClassTweakerPaths(input.readAllBytes());
					for (String path : classTweakerPaths) {
						String dedupe = metadataUrl + "::" + path;
						if (!loadedEntries.add(dedupe)) {
							continue;
						}

						byte[] content = openClassTweakerBytes(path, classLoader);
						if (content == null) {
							Contexts.LOGGER.warn("ClassTweaker file '{}' declared by '{}' was not found", path, metadataUrl);
							continue;
						}

						reader.read(content);
						loadedCount++;
						Contexts.LOGGER.debug("Loaded ClassTweaker '{}' from '{}'", path, metadataUrl);
					}
				} catch (Exception e) {
					Contexts.LOGGER.warn("Failed to process '{}': {}", metadataUrl, e.toString());
				}
			}

			return loadedCount;
		} catch (IOException e) {
			Contexts.LOGGER.warn("Failed classpath fallback scan: {}", e.toString());
			return 0;
		}
	}

	private static String stripTomlComment(String line) {
		StringBuilder out = new StringBuilder(line.length());
		boolean inSingle = false;
		boolean inDouble = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '\'' && !inDouble) {
				inSingle = !inSingle;
			} else if (c == '"' && !inSingle) {
				inDouble = !inDouble;
			} else if (c == '#' && !inSingle && !inDouble) {
				break;
			}
			out.append(c);
		}

		return out.toString();
	}

	@Nullable
	private static byte[] openClassTweakerBytes(String path, ClassLoader classLoader) throws IOException {
		URL fallback = classLoader.getResource(path);
		if (fallback == null) {
			return null;
		}

		try (InputStream stream = fallback.openStream()) {
			return stream.readAllBytes();
		}
	}

	private static String normalizePath(String path) {
		String trimmed = path.trim();
		while (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		return trimmed;
	}
}
