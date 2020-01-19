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
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.json.JSONArray
import org.json.JSONObject
import org.osgi.service.component.annotations.Component

fun main() {
    HousingCommunalService()
}

@Component
class HousingCommunalService {

    init {
        DataBaseHaTwo.insertTestData()
        start()
    }

    private fun start() {
        embeddedServer(Netty, 8088) {
            routing {
                get("test") {
                    println("--new test query--")
                    call.respondText("Hello World!", ContentType.Text.Plain)
                }
                //1
                get("counters") {
                    println("--get counters query--")
                    call.respondText(DataBaseHaTwo.counters(), ContentType.Application.Json)
                }
                //2
                get("counters/{counter}") {
                    println("--get payments query--")
                    val value = call.parameters["counter"]
                    if (value != null) {
                        call.respondText(DataBaseHaTwo.payments(value.toLong()), ContentType.Application.Json)
                    }
                }
                delete("counters/{counter}"){
                    val value = call.parameters["counter"]
                    if (value != null) {
                        DataBaseHaTwo.deleteCounter(value.toLong()
                    }
                }
                //3
                post("counters/{counter}/{dataPayment}") {
                    println("--new add payment query--")
                    val counter = call.parameters["counter"]
                    val dataPayment = call.parameters["dataPayment"]
                    if (counter != null && dataPayment != null) {
                        DataBaseHaTwo.addPayment(counter.toLong(), dataPayment.toLong())
                    }
                }
                //4
                put("counters/{counter}/{dataPayment}") {
                    println("--update payment query--")
                    val counter = call.parameters["counter"]
                    val dataPayment = call.parameters["dataPayment"]
                    if (counter != null && dataPayment != null) {
                        DataBaseHaTwo.updatePayment(counter.toLong(), dataPayment.toLong())
                    }
                }
            }
        }.start(wait = true)
    }
}

object DataBaseHaTwo {
    init {
        println("connect database")
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "org.h2.Driver") //;DB_CLOSE_DELAY=-1
        createTable()
    }

    private fun createTable() {
        println("create database")
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Counter, Payment)
        }
    }

    fun insertTestData() {
        println("insert first data")
        transaction {
            addLogger(StdOutSqlLogger)
            var id = Counter.insert {
                it[name] = "name"
                it[number] = 12345
            }
            println("new counter - $id")
            id = Counter.insert {
                it[name] = "asdf"
                it[number] = 623475
            }
            println("new counter - $id")
            id = Payment.insert {
                it[idCounter] = 12
                it[lastUpdate] = DateTime.now()
                it[counterReading] = 15L
            }
            println("new payment - $id")
        }
    }

    fun counters(): String {
        println("get counters")
        val jsonArray = JSONArray()
        transaction {
            addLogger(StdOutSqlLogger)
            println("Counter - " + Counter.selectAll().count())
            println("Payment - " + Payment.selectAll().count())
            Counter.select/*All()*/ { Counter.id.isNotNull() }.forEach { row ->
                println("counter get: ${row[Counter.id]} - ${row[Counter.name]} - ${row[Counter.number]}")

                val jsonObj = JSONObject()
                jsonObj.put("id", row[Counter.id])
                jsonObj.put("name", row[Counter.name])
                jsonObj.put("number", row[Counter.number])

                jsonArray.put(jsonObj)
            }
        }

        val jsonCounter = JSONObject()
        jsonCounter.put("counters", jsonArray)

        return jsonCounter.toString()
    }

    fun payments(number: Long): String {
        println("get payments")
        val jsonArray = JSONArray()
        transaction {
            addLogger(StdOutSqlLogger)

            Payment.select { Payment.idCounter eq number }.forEach { row ->
                println("payment get: ${row[Payment.id]} - ${row[Payment.idCounter]} - ${row[Payment.counterReading]} - ${row[Payment.lastUpdate]}")

                jsonArray.put(JSONObject().apply {
                    put("id", row[Payment.id])
                    put("idCounter", row[Payment.idCounter])
                    put("counterReading", row[Payment.counterReading])
                    put("lastUpdate", row[Payment.lastUpdate])
                })
            }
        }
        return JSONObject().put("payments", jsonArray).toString()
    }

    fun addPayment(counter: Long, counterRead: Long, date: DateTime = DateTime.now()): Long {
        println("add payment")
        var id: Long = -1
        transaction {
            addLogger(StdOutSqlLogger)
            id = Payment.insert {
                it[idCounter] = counter
                it[counterReading] = counterRead
                it[lastUpdate] = date
            } get Payment.id
            println("add payment with id - $id")
        }
        return id
    }

    fun updatePayment(counter: Long, counterRead: Long, date: DateTime = DateTime.now()): Int {
        println("update payment")
        var id: Int = -1
        transaction {
            addLogger(StdOutSqlLogger)
            if (selectPaymentDate(counter).isBefore(DateTime.now().plusDays(-2))) {
                id = Payment.update({ Payment.idCounter eq counter }) {
                    it[counterReading] = counterRead
                    it[lastUpdate] = date
                }
                println("update payment with id - $id")
            } else {
                println("payment not update")
            }
        }
        return id
    }

    private fun selectPaymentDate(counter: Long): DateTime {
        println("select payment date")
        var time = DateTime()
        transaction {
            addLogger(StdOutSqlLogger)
            time = Payment.selectAll().groupBy(Payment.id).last()[Payment.lastUpdate]
            println("get last update for counter: $counter - $time")
            //Payment.select { Payment.idCounter eq counter }.forEach {
            //time = it[Payment.lastUpdate]
            //}
        }
        return time
    }

    fun deleteCounter(idCounter: Long) {
        transaction {
            addLogger(StdOutSqlLogger)
            Counter.deleteWhere { Counter.id.eq(idCounter) }
        }
    }
}

object Counter : Table("counter") {
    val id = long("id").primaryKey().autoIncrement()

    val name = varchar("name", 50)
    val number = integer("number")
}

object Payment : Table("payment") {
    val id = long("id_payment").primaryKey().autoIncrement()

    val idCounter = long("id_counter")

    val counterReading = long("counter_reading")
    val lastUpdate: Column<DateTime> = date("last_update")
}
