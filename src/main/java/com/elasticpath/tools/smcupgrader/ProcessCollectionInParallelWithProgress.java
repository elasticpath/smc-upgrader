package com.elasticpath.tools.smcupgrader;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class ProcessCollectionInParallelWithProgress {
	/**
	 * Private constructor.
	 */
	private ProcessCollectionInParallelWithProgress() {
		// Do not instantiate
	}

	public static <T> long process(final Collection<T> input, final Function<T, Boolean> function) {
		int total = input.size();
		AtomicInteger processedCount = new AtomicInteger();
		AtomicInteger lastPrintedProgress = new AtomicInteger(0);
		AtomicLong resolvedCount = new AtomicLong();

		input.parallelStream().forEach(value -> {
			if (Boolean.TRUE.equals(function.apply(value))) {
				resolvedCount.incrementAndGet();
			}

			int current = processedCount.incrementAndGet();
			int progress = (int) ((current * 100.0) / total);

			// Print progress at each 5% milestone (but only once)
			int last = lastPrintedProgress.get();
			if (progress >= last + 5 && lastPrintedProgress.compareAndSet(last, progress - (progress % 5))) {
				System.out.println("Progress: " + (progress - (progress % 5)) + "%");
			}
		});

		return resolvedCount.get();
	}
}
