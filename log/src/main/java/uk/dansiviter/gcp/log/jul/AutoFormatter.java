package uk.dansiviter.gcp.log.jul;

import static java.util.logging.LogManager.getLogManager;
import static uk.dansiviter.jule.JulUtil.property;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Detects if running in GCP and if so format as JSON. If not this will fall back to {@link SimpleFormatter}. This uses
 * the existence of the Google Meta-Data Server hostname and switching behaviour accordingly. This can take a few
 * milliseconds to perform a DNS lookup so you can skip this by setting the environment variable {@code log.isGCP=true}.
 *
 * Example file {@code java.util.logging.config.file} config:
 *
 * <pre>
 * .level=INFO
 * handlers=uk.dansiviter.jule.AsyncConsoleHandler
 *
 * uk.dansiviter.jule.AsyncConsoleHandler.level=FINEST
 * uk.dansiviter.jule.AsyncConsoleHandler.formatter=uk.dansiviter.gcp.log.jul.AutoFormatter
 * # these will be cascaded to JsonFormatter
 * uk.dansiviter.gcp.log.jul.AutoFormatter.decorators=uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator
 * </pre>
 */
public class AutoFormatter extends Formatter {
	private static final String METADATA_HOST = "metadata.google.internal";

	private final Formatter delegate;

	public AutoFormatter() {
		if (detect()) {
			var jsonFormatter = new JsonFormatter();
			property(getLogManager(), getClass(), "decorators").ifPresent(jsonFormatter::setDecorators);
			this.delegate = jsonFormatter;
		} else {
			this.delegate = new ExpandingSimpleFormatter();
		}
	}

	@Override
	public String format(LogRecord record) {
		return this.delegate.format(record);
	}

	private static Boolean detect() {
		if (Boolean.getBoolean("log.isGcp")) {
			return true;
		}
		try {
			InetAddress.getByName(METADATA_HOST);
			return true;
		} catch (UnknownHostException e) {
			// nothing to see here
		}
		return false;
	}

	private static class ExpandingSimpleFormatter extends ExpandingFormatter {
		private final SimpleFormatter delegate = new SimpleFormatter();

		@Override
		protected String doFormat(LogRecord r) {
			return delegate.format(r);
		}
	}
}
