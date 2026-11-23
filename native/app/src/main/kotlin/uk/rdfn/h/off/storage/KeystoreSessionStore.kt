package uk.rdfn.h.off.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.rdfn.h.off.hon.SavedHonSession
import uk.rdfn.h.off.hon.asText
import uk.rdfn.h.off.hon.get
import uk.rdfn.h.off.hon.honJson
import uk.rdfn.h.off.hon.SessionStore

/**
 * Keeps the h.on refresh token encrypted with a key that never leaves the
 * phone's Android Keystore. Only the encrypted bytes are written to disk.
 *
 * If the key is lost (for example after the phone's lock screen is reset)
 * the saved sign-in can't be read, which just means signing in again.
 */
class KeystoreSessionStore(private val storage: AppStorage) : SessionStore {
	override suspend fun load(): SavedHonSession? {
		val saved = storage.readText(AppStorage.ENCRYPTED_SESSION) ?: return null
		return runCatching {
			val bytes = Base64.decode(saved, Base64.NO_WRAP)
			val initialisationVector = bytes.copyOfRange(0, GCM_IV_BYTES)
			val cipher = Cipher.getInstance(TRANSFORMATION)
			cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, initialisationVector))
			val json = honJson.parseToJsonElement(String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES)))
			val refreshToken = json["refreshToken"].asText() ?: return@runCatching null
			SavedHonSession(refreshToken, json["mobileId"].asText() ?: "")
		}.getOrNull()
	}

	override suspend fun save(session: SavedHonSession) {
		val json = buildJsonObject {
			put("refreshToken", session.refreshToken)
			put("mobileId", session.mobileId)
		}.toString()
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
		// The Keystore picks a fresh random IV each time; it is stored in front of the data.
		val encrypted = cipher.iv + cipher.doFinal(json.toByteArray())
		storage.writeText(AppStorage.ENCRYPTED_SESSION, Base64.encodeToString(encrypted, Base64.NO_WRAP))
	}

	override suspend fun clear() {
		storage.writeText(AppStorage.ENCRYPTED_SESSION, null)
	}

	private fun getOrCreateKey(): SecretKey {
		val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
		(keyStore.getKey(KEY_ALIAS, null) as SecretKey?)?.let { return it }
		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
		generator.init(
			KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build(),
		)
		return generator.generateKey()
	}

	private companion object {
		const val ANDROID_KEYSTORE = "AndroidKeyStore"
		const val KEY_ALIAS = "h-off-session"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val GCM_IV_BYTES = 12
		const val GCM_TAG_BITS = 128
	}
}
