package uk.dansiviter.gcp.log.log4j2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static uk.dansiviter.gcp.log.Factory.logEntry;
import static uk.dansiviter.gcp.log.JsonFactory.toJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.log4j2.Log4j2Appender.Log4J2Entry;

@Plugin(name = "CloudLogging", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class JsonLayout extends AbstractStringLayout {
	static final String CONTENT_TYPE = "application/json";

	private final List<EntryDecorator> decorators = new LinkedList<>();

	/**
	 *
	 * @param builder the builder.
	 */
	protected JsonLayout(Builder<?> builder) {
		super(UTF_8);
		Optional.ofNullable(builder.decorators).map(Log4j2Appender::decorators).ifPresent(this.decorators::addAll);
	}

	@Override
	public String toSerializable(LogEvent event) {
		var entry = logEntry(new Log4J2Entry(event), this.decorators);

		try (var os = new ByteArrayOutputStream()) {
			toJson(entry, os);
			return os.toString(UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}


	// --- Static Methods ---

	/**
	 * New builder instance.
	 *
	 * @param <B> builder type.
	 * @return a new instance of the builder.
	 */
	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}


	// --- Inner Classes ---

	/**
	 * A Log4j entry.
	 */
	public static class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B>
		implements org.apache.logging.log4j.core.util.Builder<JsonLayout> {

		@PluginElement("Decorators")
		private DecoratorItem[] decorators;

		/**
		 * @param decorators the decorators to set.
		 * @return this builder.
		 */
		public B setDecorators(DecoratorItem... decorators) {
			this.decorators = decorators;
			return asBuilder();
		}

		@Override
		public JsonLayout build() {
			return new JsonLayout(this);
		}
	}
}
