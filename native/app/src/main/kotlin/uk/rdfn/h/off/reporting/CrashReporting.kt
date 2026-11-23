package uk.rdfn.h.off.reporting

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import uk.rdfn.h.off.BuildConfig
import uk.rdfn.h.off.diagnostics.PrivateDetails

/**
 * Crash reports, sent to Sentry (sentry.io) only when you've switched
 * "Crash reports" on in Settings. Until then Sentry is never started (its
 * automatic start-up is turned off in the manifest), so nothing is sent.
 *
 * Reports carry the error, where in the app it happened and what the app
 * did just before. Screenshots, the screen layout, session tracking and
 * performance tracing are all off, and every report has MAC addresses and
 * email addresses removed before it leaves the phone.
 */
class CrashReporting(private val context: Context) {
	private var isRunning = false

	/** False when this build has no Sentry address, so the Settings switch is disabled. */
	val isSetUp: Boolean get() = BuildConfig.SENTRY_DSN.isNotEmpty()

	/** Starts or stops crash reporting straight away, e.g. when the Settings switch changes. */
	fun setEnabled(isOn: Boolean) {
		val shouldRun = isOn && isSetUp
		if (shouldRun && !isRunning) {
			SentryAndroid.init(context) { options ->
				options.dsn = BuildConfig.SENTRY_DSN
				// Personal details (IP address, user) are off by default in this SDK version.
				options.release = "uk.rdfn.h.off@${BuildConfig.VERSION_NAME}+${BuildConfig.BUILD_NUMBER.takeIf { it > 0 } ?: "local"}"
				options.isAttachScreenshot = false
				options.isAttachViewHierarchy = false
				options.isEnableAutoSessionTracking = false
				options.isEnableUserInteractionBreadcrumbs = false
				options.isEnableUserInteractionTracing = false
				// Only crashes and errors, no performance data.
				options.tracesSampleRate = 0.0
				options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
					event.message?.let { message ->
						message.formatted = message.formatted?.let(PrivateDetails::redactText)
						message.message = message.message?.let(PrivateDetails::redactText)
					}
					event.exceptions?.forEach { exception -> exception.value = exception.value?.let(PrivateDetails::redactText) }
					event.breadcrumbs?.forEach { breadcrumb -> breadcrumb.message = breadcrumb.message?.let(PrivateDetails::redactText) }
					event.user = null
					event.serverName = null
					event
				}
			}
			isRunning = true
		} else if (!shouldRun && isRunning) {
			Sentry.close()
			isRunning = false
		}
	}
}
