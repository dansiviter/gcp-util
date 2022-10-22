package uk.dansiviter.gcp.log.jul;

import static java.lang.Thread.currentThread;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.util.logging.LogManager.getLogManager;
import static uk.dansiviter.jule.JulUtil.property;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Detects if running in GCP and if so format as JSON. If not this will fall back to {@link SimpleFormatter}.
 *
 * Example file {@code java.util.logging.config.file} config:
 *
 * <pre>
 * .level=INFO
 * handlers=uk.dansiviter.jule.AsyncConsoleHandler
 *
 * uk.dansiviter.jule.AsyncConsoleHandler.level=FINEST
 * uk.dansiviter.jule.AsyncConsoleHandler.formatter=uk.dansiviter.gcp.log.AutoFormatter
 * # these will be cascaded to JsonFormatter
 * uk.dansiviter.gcp.log.AutoFormatter.decorators=uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator
 * </pre>
 */
public class AutoFormatter extends Formatter {
	private static final URI METADATA_URI = URI.create("http://metadata.google.internal");
	private static final Duration TIMEOUT = Duration.ofMillis(250);

	private final ErrorManager errorManager = new ErrorManager();

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

	private Boolean detect() {
		try {
			var req = HttpRequest
				.newBuilder(METADATA_URI)
				.headers("Metadata-Flavor", "Google")
				.timeout(TIMEOUT)
				.GET();
			var client = HttpClient
				.newBuilder()
				.connectTimeout(TIMEOUT)
				.build();
			client.send(req.build(), discarding());
			return true;
		} catch (IOException e) {
			// nothing to see here
		} catch (InterruptedException e) {
			errorManager.error("Error detecting GCP!", e, ErrorManager.OPEN_FAILURE);
			currentThread().interrupt();
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
