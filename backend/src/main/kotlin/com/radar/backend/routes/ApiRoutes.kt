package com.radar.backend.routes

import com.radar.backend.models.HealthDto
import com.radar.backend.service.DemandService
import com.radar.backend.service.YaRadarDemandEngine
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.apiRoutes(service: DemandService) {
    get("/") {
        call.respond(service.apiInfo())
    }

    get("/health") {
        call.respond(
            HealthDto(
                status = "ok",
                officialProviderConfigured = service.officialConfigured,
                note = "YaRaDaR Official API · ${YaRadarDemandEngine.SOURCE_NAME}"
            )
        )
    }

    get("/v1/info") {
        call.respond(service.apiInfo())
    }

    authenticate("auth-bearer", optional = true) {
        route("/v1") {
            get("/countries") {
                call.respond(service.getCountries())
            }
            get("/countries/{code}/cities") {
                val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond(service.getCities(code))
            }
            get("/cities") {
                val q = call.request.queryParameters["q"]
                call.respond(
                    if (q.isNullOrBlank()) service.getAllCities()
                    else service.searchCities(q)
                )
            }
            get("/cities/detect") {
                val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
                if (lat == null || lon == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lat/lon required"))
                    return@get
                }
                call.respond(service.detectCity(lat, lon))
            }
            get("/cities/{cityId}/zones") {
                val cityId = call.parameters["cityId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond(service.getZones(cityId))
            }
            get("/zones/{zoneId}") {
                val zoneId = call.parameters["zoneId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val zone = service.getZone(zoneId)
                if (zone == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "zone_not_found"))
                else call.respond(zone)
            }
            get("/cities/{cityId}/realtime") {
                val cityId = call.parameters["cityId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respond(service.isRealtime(cityId))
            }
            delete("/users/{userId}") {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
