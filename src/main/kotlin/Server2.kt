package org.example

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.json.JSONArray
import org.json.JSONObject
import org.osgi.service.component.annotations.Component

@Component
class KinomaxService {

    init {
        DataBase.insertTestData()
        start()
    }

    private fun start() {
        embeddedServer(Netty, 8088) {
            routing {
                //список всех спектаклей
                get("performances") {
                    call.respondText(DataBase.performances(), ContentType.Text.Plain)
                }
                //список мест на спектакль
                get("places/{performId}") {
                    val value = call.parameters["performId"]
                    if (value != null) {
                        call.respondText(DataBase.places(value.toLong()), ContentType.Text.Plain)
                    }
                }
                //получить цену на место
                get("place/{performId}/{placeId}") {
                    val performId = call.parameters["performId"]
                    val place = call.parameters["placeId"]
                    if (performId != null && place != null) {
                        val price = DataBase.placePrice(performId.toLong(), place.toLong())
                        call.respondText(
                            if (price == -1) "попробуйте еще раз" else price.toString(),
                            ContentType.Text.Plain
                        )
                    }
                }
                //бронирование
                post("performance/{performId}/{placeId}") {
                    val performId = call.parameters["performId"]
                    val place = call.parameters["placeId"]
                    if (performId != null && place != null) {
                        DataBase.reservation(performId.toLong(), place.toLong())
                    }
                }
                //отмена бронирования
                delete("performance/{performId}/{placeId}") {
                    val performance = call.parameters["performId"]
                    val place = call.parameters["placeId"]
                    if (performance != null && place != null) {
                        DataBase.scheduling(performance.toLong(), place.toLong())
                    }
                }
                //поменять забронированное место
                put("performance/{oldPlace}/{newPlace}") {
                    val oldPlace = call.parameters["oldPlace"]
                    val newPlace = call.parameters["newPlace"]
                    if (oldPlace != null && newPlace != null) {
                        DataBase.change(oldPlace.toLong(), newPlace.toLong())
                    }
                }
            }
        }.start(wait = true)
    }
}

object DataBase {
    init {
        println("connect database")
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        createTable()
    }

    private fun createTable() {
        println("create database")
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Performance, Place)
        }
    }

    fun insertTestData() {
        println("insert first data")
        transaction {
            addLogger(StdOutSqlLogger)
            var id = Performance.insert {
                it[performanceName] = "Ololoev Ololo"
            }
            println("new counter - $id")
            id = Place.insert {
                it[idPerformance] = 1
                it[placeDate] = DateTime.now()
            }
            println("new counter - $id")
        }
    }

    fun performances(): String {
        val jsonArray = JSONArray()
        transaction {
            addLogger(StdOutSqlLogger)
            Performance.selectAll().forEach { row ->

                val jsonObj = JSONObject()
                jsonObj.put("id", row[Performance.id])
                jsonObj.put("name", row[Performance.performanceName])
                jsonObj.put("date", row[Performance.performanceDate])
                jsonObj.put("places", row[Performance.places])

                jsonArray.put(jsonObj)
            }
        }

        val jsonCounter = JSONObject()
        jsonCounter.put("performances", jsonArray)

        return jsonCounter.toString()
    }

    fun places(idPerf: Long): String {
        println("get places")
        val jsonArray = JSONArray()
        transaction {
            addLogger(StdOutSqlLogger)

            Place.select { Place.idPerformance.eq(idPerf) }.forEach { rowPlace ->
                val perfName = Performance.select { Performance.id.eq(idPerf) }.single()[Performance.performanceName]

                val jsonObj = JSONObject()
                jsonObj.put("id", rowPlace[Place.id])
                jsonObj.put("performanceName", perfName)

                jsonArray.put(jsonObj)
            }
        }
        return JSONObject().put("places", jsonArray).toString()
    }

    fun placePrice(idPerf: Long, idPlace: Long): Int {
        var price = -1
        transaction {
            addLogger(StdOutSqlLogger)

            price = Place.select { Place.idPerformance.eq(idPerf) and Place.id.eq(idPlace) }.single()[Place.price]
        }
        return price
    }

    fun reservation(idPerf: Long, idPlace: Long) {
        transaction {
            addLogger(StdOutSqlLogger)

            Place.update({
                Place.idPerformance.eq(idPerf) and Place.id.eq(idPlace) and Place.placeDate.isNull() and Place.buy.eq(
                    false
                )
            }) {
                it[placeDate] = DateTime.now()
            }
        }
    }

    fun scheduling(idPerf: Long, idPlace: Long) {
        println("scheduling places")
        transaction {
            addLogger(StdOutSqlLogger)

            Place.update({ Place.idPerformance.eq(idPerf) and Place.id.eq(idPlace) and Place.placeDate.isNotNull() }) {
                it[placeDate] = null
            }
        }
    }

    fun change(oldPlace: Long, newPlace: Long) {
        println("change place")
        transaction {
            addLogger(StdOutSqlLogger)

            Place.update({ Place.id.eq(oldPlace) and Place.placeDate.isNotNull() }) {
                it[placeDate] = null
            }

            Place.update({ Place.id.eq(newPlace) and Place.placeDate.isNull() }) {
                it[placeDate] = DateTime.now()
            }
        }
    }
}

object Performance : Table("performance") {
    val id: Column<Long> = long("id").primaryKey().autoIncrement()

    val performanceName: Column<String> = varchar("performance_name", 250)
    val performanceDate: Column<DateTime> = Place.date("performance_date")
    val places: Column<Int> = integer("places")
}

object Place : Table("place") {
    val id: Column<Long> = long("id").primaryKey().autoIncrement()

    val price: Column<Int> = Performance.integer("price")
    val idPerformance: Column<Long> = long("id_performance")
    val placeDate:Column<DateTime?> = date("place_date").nullable()
    val buy:Column<Boolean> = bool("buy")
}