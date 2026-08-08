package za.co.neroland.nerofactions.telemetry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Log4j2 appender that feeds {@link NeroFactionsTelemetry}. Minecraft routes essentially every
 * failure through log4j — handled errors, event-bus listener exceptions, and the crash report
 * itself — so listening on the root logger catches NeroFactions failures without mixins. Filtering
 * (NeroFactions-only), de-dup, rate-limiting and PII scrubbing all happen in
 * {@link NeroFactionsTelemetry}; this only selects candidate log events.
 */
final class SentryLogAppender extends AbstractAppender {

    SentryLogAppender() {
        super("NeroFactionsSentry", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        if (!NeroFactionsTelemetry.isActive()) {
            return;
        }
        Level level = event.getLevel();
        if (!level.isMoreSpecificThan(Level.ERROR)) {
            return;
        }
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            if (NeroFactionsTelemetry.touchesNeroFactions(thrown)) {
                NeroFactionsTelemetry.capture(thrown);
            }
        } else if (level == Level.FATAL) {
            String message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
            if (message != null && message.contains("za.co.neroland.nerofactions")) {
                NeroFactionsTelemetry.captureMessage(message);
            }
        }
    }
}
