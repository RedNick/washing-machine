package uk.rdfn.h.off.hon

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.rdfn.h.off.hon.HonFixtures.runningContext
import uk.rdfn.h.off.hon.HonFixtures.sampleCatalogue
import uk.rdfn.h.off.hon.HonFixtures.washerAppliance
import uk.rdfn.h.off.washer.MachineInfo
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.WasherCommandException

/** Ported from src/hon/__tests__/honCloudWasher.test.ts. */
class HonCloudWasherTest {
	/** A pretend answer from Haier's servers. */
	private data class FakeAnswer(val body: JsonElement, val status: Int = 200)

	/** Each test says how the h.on servers should answer; null means "use the healthy default". */
	private fun interface FakeRoute {
		fun answer(request: HttpRequest): FakeAnswer?
	}

	/** A pretend network that records every request. */
	private class FakeNetwork(private val route: FakeRoute) : HttpSender {
		val requests = mutableListOf<HttpRequest>()
		override suspend fun send(request: HttpRequest): HttpResponse {
			requests += request
			val answer = route.answer(request) ?: error("Unexpected request to ${request.url}")
			return HttpResponse(answer.status, answer.body.toString())
		}
	}

	private class MemoryStore(var saved: SavedHonSession? = null) : SessionStore {
		override suspend fun load() = saved
		override suspend fun save(session: SavedHonSession) {
			saved = session
		}
		override suspend fun clear() {
			saved = null
		}
	}

	private fun payload(content: JsonElement) = buildJsonObject { put("payload", content) }

	/** Answers for a healthy account with one washing machine. */
	private fun healthyAccount(extra: FakeRoute = FakeRoute { null }): FakeRoute {
		var cognitoCounter = 0
		return FakeRoute { request ->
			val url = request.url
			extra.answer(request) ?: when {
				"/services/oauth2/token" in url -> FakeAnswer(buildJsonObject {
					put("id_token", "new-id-token")
					put("access_token", "a")
				})
				url.endsWith("/auth/v1/login") -> FakeAnswer(buildJsonObject {
					putJsonObject("cognitoUser") { put("Token", "cognito-${++cognitoCounter}") }
				})
				url.endsWith("/commands/v1/appliance") -> FakeAnswer(payload(buildJsonObject {
					put("appliances", buildJsonArray {
						add(buildJsonObject {
							put("applianceTypeName", "REF")
							put("macAddress", "fridge")
						})
						add(washerAppliance)
					})
				}))
				"/commands/v1/retrieve" in url ->
					FakeAnswer(payload(JsonObject(sampleCatalogue + ("resultCode" to kotlinx.serialization.json.JsonPrimitive("0")))))
				"/commands/v1/context" in url -> FakeAnswer(payload(runningContext))
				"/commands/v1/send" in url -> FakeAnswer(payload(buildJsonObject { put("resultCode", "0") }))
				else -> null
			}
		}
	}

	private suspend fun connectedWasher(extra: FakeRoute = FakeRoute { null }): Pair<HonCloudWasher, FakeNetwork> {
		val network = FakeNetwork(healthyAccount(extra))
		val session = HonSession.restore(MemoryStore(SavedHonSession("refresh-1", "phone")), network)!!
		return HonCloudWasher.connect(session, network) to network
	}

	@Test
	fun signsInSwapsTheIdTokenForACognitoTokenAndSavesOnlyTheRefreshToken() = runTest {
		val store = MemoryStore()
		val network = FakeNetwork(healthyAccount())
		val session = HonSession.fromLogin(HonLoginTokens("a", "refresh-1", "id-1"), store, network)

		val loginRequest = network.requests.first { it.url.endsWith("/auth/v1/login") }
		assertEquals("id-1", loginRequest.headers["id-token"])
		assertEquals("refresh-1", store.saved?.refreshToken)
		assertTrue(Regex("^[0-9a-f]{16}$").matches(store.saved!!.mobileId))

		val headers = session.authorizedHeaders()
		assertEquals("cognito-1", headers["cognito-token"])
		assertEquals("id-1", headers["id-token"])
	}

	@Test
	fun returnsNullWhenNothingIsSaved() = runTest {
		assertNull(HonSession.restore(MemoryStore(), FakeNetwork(healthyAccount())))
	}

	@Test
	fun reportsAnExpiredSignInWhenTheRefreshIsRefused() = runTest {
		val network = FakeNetwork(healthyAccount { request ->
			if ("/oauth2/token" in request.url) FakeAnswer(JsonObject(emptyMap()), status = 400) else null
		})
		try {
			HonSession.restore(MemoryStore(SavedHonSession("old", "phone")), network)
			fail("Expected the expired sign-in to be reported")
		} catch (expected: HonAuthException) {
			// The app shows the sign-in screen for this.
		}
	}

	@Test
	fun findsTheWashingMachineAmongOtherAppliances() = runTest {
		val (washer) = connectedWasher()
		// The nickname is a placeholder, so the model name is used.
		assertEquals(MachineInfo("H7W 412MBCR-80", "H7W 412MBCR-80", "Hoover"), washer.getMachineInfo())
	}

	@Test
	fun failsClearlyWhenThereIsNoWashingMachine() = runTest {
		val network = FakeNetwork(healthyAccount { request ->
			if (request.url.endsWith("/commands/v1/appliance")) {
				FakeAnswer(payload(buildJsonObject { put("appliances", buildJsonArray { }) }))
			} else {
				null
			}
		})
		val session = HonSession.restore(MemoryStore(SavedHonSession("r", "phone")), network)!!
		try {
			HonCloudWasher.connect(session, network)
			fail("Expected NoWasherFoundException")
		} catch (expected: NoWasherFoundException) {
			// The sign-in screen explains this.
		}
	}

	@Test
	fun readsTheStatusWithTheProgramNameFromTheCatalogue() = runTest {
		val (washer, network) = connectedWasher()
		val status = washer.getStatus()
		assertEquals(MachineState.RUNNING, status.state)
		assertEquals("Cottons", status.programName)

		val retrieveRequest = network.requests.first { "/commands/v1/retrieve" in it.url }
		// The model code comes from the serial number when h.on doesn't send one.
		assertTrue(retrieveRequest.url.contains("code=AB1CDEF2345"))
		assertTrue(retrieveRequest.url.contains("firmwareId=41"))
	}

	@Test
	fun downloadsTheLargeProgramCatalogueOnlyOnce() = runTest {
		val (washer, network) = connectedWasher()
		washer.getStatus()
		washer.getPrograms()
		washer.getStatus()
		assertEquals(1, network.requests.count { "/commands/v1/retrieve" in it.url })
	}

	@Test
	fun refreshesTheTokensAndRetriesOnceWhenHonSaysTheyAreStale() = runTest {
		var contextRequests = 0
		val (washer, network) = connectedWasher { request ->
			if ("/commands/v1/context" !in request.url) return@connectedWasher null
			contextRequests += 1
			if (contextRequests == 1) FakeAnswer(JsonObject(emptyMap()), status = 401) else null
		}
		assertEquals(MachineState.RUNNING, washer.getStatusWithoutProgramName().state)
		assertEquals(2, contextRequests)
		// One refresh when restoring, one more after the 401.
		assertEquals(2, network.requests.count { "/oauth2/token" in it.url })
	}

	@Test
	fun sendsAStartCommandAndChecksThatTheMachineAcceptedIt() = runTest {
		val (washer, network) = connectedWasher()
		washer.startProgram("PROGRAMS.WM_WD.COTTONS", mapOf("temp" to "60"))

		val sentBody = honJson.parseToJsonElement(network.requests.first { "/commands/v1/send" in it.url }.body!!)
		assertEquals("PROGRAMS.WM_WD.COTTONS", sentBody["programName"].asText())
		assertEquals("60", sentBody["parameters"]["temp"].asText())
		assertEquals("phone", sentBody["device"]["mobileId"].asText())
	}

	@Test
	fun explainsARefusedCommand() = runTest {
		val (washer) = connectedWasher { request ->
			if ("/commands/v1/send" in request.url) FakeAnswer(payload(buildJsonObject { put("resultCode", "1") })) else null
		}
		try {
			washer.stop()
			fail("Expected the refusal to be reported")
		} catch (refusal: WasherCommandException) {
			assertTrue(refusal.message!!.contains("remote control"))
		}
		try {
			washer.startProgram("PROGRAMS.NOPE", emptyMap())
			fail("Expected an unknown program to be refused")
		} catch (expected: WasherCommandException) {
			// Unknown program.
		}
	}
}
