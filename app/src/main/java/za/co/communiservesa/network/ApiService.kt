package za.co.communiservesa.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class AuthRequest(val name: String = "", val email: String, val password: String)
data class AuthResponse(val token: String, val user: UserDto)
data class PendingItem(val type: String, val id: String, val status: String, val title: String = "")
data class SsoRequest(val email: String, val name: String, val provider: String = "google", val idToken: String = "")
data class DeviceRequest(val token: String)
data class ProfileUpdate(val name: String? = null, val language: String? = null, val notifications: Boolean? = null, val radius: Int? = null)
data class UserDto(val id: String, val name: String, val email: String, val language: String = "English", val notifications: Boolean = true, val radius: Int = 10, val contributions: Int = 0)
data class ServiceDto(
    val id: String,
    val title: String,
    val category: String,
    val description: String = "",
    val phone: String = "",
    val rating: Double = 0.0,
    val distanceKm: Double = 0.0,
    val verified: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null
)
data class EventDto(
    val id: String,
    val title: String,
    val category: String,
    val description: String = "",
    val venue: String = "",
    val dateTime: String = "",
    val distanceKm: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null
)
data class ReviewRequest(val rating: Int, val text: String)
data class ReviewDto(val id: String, val serviceId: String, val rating: Int, val text: String, val author: String)
data class CreateListingRequest(
    val title: String,
    val category: String,
    val description: String,
    val phone: String,
    val type: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)
data class MessageResponse(val message: String)
/**
 * Retrofit interface describing the REST API contract between the Android
 * client and the Node.js/Express backend (see server/server.js). Each
 * function maps to one HTTP endpoint; Retrofit generates the network code
 * at compile time. Reference: https://square.github.io/retrofit/
 */
interface ApiService {
    // Used by the "Save & Test Connection" button to confirm a physical
    // device can actually reach the server before attempting to sign in.
    @GET("health") suspend fun health(): MessageResponse
    @POST("api/auth/register") suspend fun register(@Body request: AuthRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body request: AuthRequest): AuthResponse
    // Single sign-on: exchanges a verified Google identity for a CommuniServe JWT.
    @POST("api/auth/sso") suspend fun sso(@Body request: SsoRequest): AuthResponse
    // Registers this phone's FCM token so the backend can target push notifications.
    @POST("api/devices") suspend fun registerDevice(@Body request: DeviceRequest): MessageResponse
    @GET("api/services") suspend fun services(@Query("q") q: String = "", @Query("radius") radius: Int = 10): List<ServiceDto>
    @GET("api/services/{id}") suspend fun service(@Path("id") id: String): ServiceDto
    @POST("api/services") suspend fun addService(@Body request: CreateListingRequest): ServiceDto
    @GET("api/events") suspend fun events(@Query("q") q: String = "", @Query("radius") radius: Int = 10): List<EventDto>
    @POST("api/events") suspend fun addEvent(@Body request: CreateListingRequest): EventDto
    @GET("api/reviews/{serviceId}") suspend fun reviews(@Path("serviceId") serviceId: String): List<ReviewDto>
    @POST("api/reviews/{serviceId}") suspend fun addReview(@Path("serviceId") serviceId: String, @Body request: ReviewRequest): ReviewDto
    @GET("api/users/profile") suspend fun profile(): UserDto
    @PUT("api/users/profile") suspend fun updateProfile(@Body update: ProfileUpdate): UserDto

    @GET("api/pending") suspend fun pending(): List<PendingItem>

    @POST("api/pending/{id}/approve") suspend fun approve(@Path("id") id: String): PendingItem
}
