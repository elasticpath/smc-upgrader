package com.elasticpath.tools.smcupgrader;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class ProcessCollectionInSerialWithProgress {
	private static final double ONE_HUNDRED_PERCENT = 100.0;
	private static final int PROGRESS_INCREMENT = 5;

	/**
	 * Private constructor.
	 */
	private ProcessCollectionInSerialWithProgress() {
		// Do not instantiate
	}

	/**
	 * Process the input collection using the passed function, outputting progress in 5% increments to standard out.
	 *
	 * @param input the collection to process
	 * @param function the function to evaluate on each record of the collection, returning true if the function evaluation "completed"
	 * @return the number of values that were "completed"
	 * @param <T> the value type
	 */
	public static <T> long process(final Collection<T> input, final Function<T, Boolean> function) {
		int total = input.size();
		AtomicInteger processedCount = new AtomicInteger();
		AtomicInteger lastPrintedProgress = new AtomicInteger(0);
		AtomicLong resolvedCount = new AtomicLong();

		input.forEach(value -> {
			if (Boolean.TRUE.equals(function.apply(value))) {
				resolvedCount.incrementAndGet();
			}

			int current = processedCount.incrementAndGet();
			int progress = (int) ((current * ONE_HUNDRED_PERCENT) / total);

			// Print progress at each 5% milestone (but only once)
			int last = lastPrintedProgress.get();
			if (progress >= last + PROGRESS_INCREMENT
					&& lastPrintedProgress.compareAndSet(last, progress - (progress % PROGRESS_INCREMENT))) {
				System.out.println("Progress: " + (progress - (progress % PROGRESS_INCREMENT)) + "%");
			}
		});

		return resolvedCount.get();
	}
}
