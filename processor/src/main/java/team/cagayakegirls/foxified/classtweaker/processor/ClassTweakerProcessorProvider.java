package team.cagayakegirls.foxified.classtweaker.processor;

import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;

public final class ClassTweakerProcessorProvider implements ClassProcessorProvider {
	@Override
	public void createProcessors(Context context, Collector collector) {
		ClassTweakerProcessor processor = ClassTweakerProcessor.create();
		if (processor != null) {
			collector.add(processor);
		}
	}
}
