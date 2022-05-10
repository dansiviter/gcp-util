package uk.dansiviter.gcp.log;

/**
 * Utility class for decorators.
 */
public enum Decorators { ;
	/**
	 * A decorator that appends {@code serviceContext}.
	 *
	 * @param service the service name.
	 * @param version the service version.
	 * @return a decorator instance.
	 */
	public static EntryDecorator serviceContext(String service, String version) {
		return new ServiceContextDecorator(service,  version);
	}

	/**
   * A message masking decorator for the given patterns.
	 *
	 * @param patterns regular expression patterns.
	 * @return a decorator instance.
	 */
	public static EntryDecorator masking(String... patterns) {
		return new MessageMaskingDecorator(patterns);
	}
}
