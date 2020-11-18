package uk.dansiviter.gcp.monitoring.log.jul;

import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A formatter that will expand {@link Supplier} instances.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public abstract class ExpandingFormatter extends Formatter {
	@Override
	public final String format(LogRecord record) {
		// expand any suppliers so we can lazy extract values
		if (record.getParameters() != null) {
			var params = record.getParameters();
			for (int i = 0; i < params.length; i++) {
				if (params[i] instanceof Supplier) {
					params[i] = ((Supplier<?>) params[i]).get();
				}
			}
		}
		return doFormat(record);
	}

	protected abstract String doFormat(LogRecord record);
}
