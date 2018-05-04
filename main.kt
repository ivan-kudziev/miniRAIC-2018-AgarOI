import org.json.JSONObject
import java.awt.Point

import java.io.BufferedWriter
import java.io.File


enum class CircleType {
    FOOD, EJECT, VIRUS, PLAYER
}

const val SPIRIT_AXIST_TIME = 20
val dangetBorderDist: Int = 50

var LOG: String = ""
var ticCount: Int = 0


val myFrags: MutableList<PlUnit> = mutableListOf<PlUnit>()
val enemyFrags: MutableList<PlUnit> = mutableListOf<PlUnit>()
val oldEnemyFrags: MutableList<PlUnit> = mutableListOf<PlUnit>()
val food: MutableList<Food> = mutableListOf<Food>()
val virus: MutableList<Virus> = mutableListOf<Virus>()

var lastSawEnemyTimer: Int = 0
var prSweatDistanse: Float = 0f
var prDangerDistanse: Float = 0f

var closerToCenterNoFoodTimer: Int = 0
var lastCallNoFoodTick: Int = 0

var enemyRunAlpha = Math.PI / 32
var runRotateDir = 1

var split = false

var lastEnemyWigth: Float = 0F
var alpha: Float = 0F
var rad: Double = 40.0
var velKaf: Int = 1

var wConfig: WorldConst? = null


fun main(args: Array<String>) {
    addToLOG("Start | ", 1)
    val config = JSONObject(readLine())

    wConfig = WorldConst(config["GAME_WIDTH"] as Int,
            config["GAME_HEIGHT"] as Int,
            config["GAME_TICKS"] as Int,
            castToFloat(config["FOOD_MASS"]),
            config["MAX_FRAGS_CNT"] as Int,
            config["TICKS_TIL_FUSION"] as Int,
            castToFloat(config["VIRUS_RADIUS"]),
            castToFloat(config["VIRUS_SPLIT_MASS"]),
            castToFloat(config["VISCOSITY"]),
            castToFloat(config["INERTION_FACTOR"]),
            castToFloat(config["SPEED_FACTOR"]))

    if (wConfig!!.INERTION_FACTOR > 10 && wConfig!!.SPEED_FACTOR > 50) velKaf = 1 else velKaf = 3


    while (true) {
        ticCount++
        lastSawEnemyTimer -= if (lastSawEnemyTimer != 0) 1 else 0
        lastSawEnemyTimer = if (enemyFrags.size > 0) 20 else lastSawEnemyTimer
        addToLOG(" ~~~~$ticCount -->>> ", 7)
        // sendToFile()

        if (ticCount > 0) {

            val tickData = JSONObject(readLine())
            gameObjectsClean()
            gameObjectsInit(tickData)

            var move = onTick(tickData)
            if (move == null || move.equals("null")) {
                move = JSONObject(mapOf("X" to 0, "Y" to 0, "Debug" to "not Died $LOG"))
                LOG = ""
                println(move)
            } else {
                LOG = ""
                println(move)
            }
        } else {
            val tickData = JSONObject(readLine())
            val move = onTick(tickData)
            println(move)
        }
    }
}


fun gameObjectsClean() {

    myFrags.clear()
    oldEnemyFrags.clear()
    oldEnemyFrags.addAll(enemyFrags)
    enemyFrags.clear()
    food.clear()
    virus.clear()

}

fun gameObjectsInit(jsonObject: JSONObject) {
    val mine = jsonObject.getJSONArray("Mine")!!
    val objects = jsonObject.getJSONArray("Objects")!!
    if (mine.length() != 0) {
        for ((i, frag) in mine.withIndex()) {
            myFrags.add(PlUnit())
            myFrags[i].updateFromJson(true, frag as JSONObject)
        }

        for (frag in objects) {
            when ((frag as JSONObject)["T"]) {
                "F" -> {
                    food.add(Food())
                    food.last().updateFromJson(true, frag)
                }
                "P" -> {
                    enemyFrags.add(PlUnit())
                    enemyFrags.last().updateFromJson(false, frag)
                    for (oldE in oldEnemyFrags) {
                        if (oldE.id == enemyFrags.last().id) {
                            enemyFrags.last().speedX = enemyFrags.last().x - oldE.x
                            enemyFrags.last().speedY = enemyFrags.last().y - oldE.y
                            enemyFrags.last().spiritExistTimer = -1
                        }

                    }
                }
                "V" -> {
                    virus.add(Virus())
                    virus.last().updateFromJson(frag)
                }


            }


        }
        for (oldFrag in oldEnemyFrags) {
            oldFrag.spiritExistTimer += if (oldFrag.spiritExistTimer > 0) -1 else 0
        }

        var existFlag: Int
        for (oldFrag in oldEnemyFrags) {
            existFlag = -1
            for (frag in enemyFrags) {
                if (oldFrag.id == frag.id) {
                    existFlag = 1
                    break
                }
            }

            if (existFlag == -1) {
                if (oldFrag.spiritExistTimer != 0) {
                    enemyFrags.add(oldFrag.clone())

                    if (oldFrag.spiritExistTimer == -1) {

                        enemyFrags.last().spiritExistTimer = SPIRIT_AXIST_TIME
                    } else {
                        enemyFrags.last().spiritExistTimer = oldFrag.spiritExistTimer

                        var nearestMySwatFrag: PlUnit = myFrags[0]
                        var minSwatDist: Float = Float.MAX_VALUE
                        var nearestMyRunFrag: PlUnit = myFrags[0]
                        var minRunDist: Float = Float.MAX_VALUE
                        for (mFrags in myFrags) {
                            if (enemyFrags.last().dist(mFrags) < minRunDist && enemyFrags.last().canEat(mFrags)) {
                                nearestMyRunFrag = mFrags
                                minRunDist = enemyFrags.last().dist2(mFrags)
                            }

                            if (enemyFrags.last().dist(mFrags) < minSwatDist && mFrags.canEat(enemyFrags.last())) {
                                nearestMySwatFrag = mFrags
                                minSwatDist = enemyFrags.last().dist2(mFrags)
                            }
                        }
                        val target: Pair<Float, Float>
                        target = if (minRunDist > minSwatDist) {
                            fetcherSpeedXY(enemyFrags.last(), Pair(nearestMyRunFrag.x + nearestMyRunFrag.speedX, nearestMyRunFrag.y + nearestMyRunFrag.speedY))
                        } else {
                            fetcherSpeedXY(enemyFrags.last(), Pair(enemyFrags.last().x + nearestMySwatFrag.speedX, enemyFrags.last().y + nearestMySwatFrag.speedY))

                        }

                        enemyFrags.last().speedX = target.first
                        enemyFrags.last().speedY = target.second
                        enemyFrags.last().x += target.first
                        enemyFrags.last().y += target.second
                    }
                }
            }
        }
    }

}

fun castToFloat(obj: Any): Float {
    return when (obj) {
        is Int -> obj.toFloat()
        is Double -> obj.toFloat()
        is Float -> obj.toFloat()

        else -> 0F
    }

}

fun castToDouble(obj: Any): Double {
    return when (obj) {
        is Int -> obj.toDouble()
        is Double -> obj.toDouble()
        is Float -> obj.toDouble()

        else -> 0.0
    }

}


fun onTick(tickData: JSONObject): JSONObject {
    split = false
    if (myFrags.size != 0) {
        val food =  eatSweetEnemy() ?: goToVirus()
        ?: (if (myFrags.size > 1) findNearestFood() else (findFastedGetFood3vel() ?: findNearestFood()))
        ?: noFoodMoveSpiral()


        food.target = getFinalTargetPoint(food.target, food.actionOwner)

        addToLOG("|=" + food.comment + "==>(${food.target.first} ; ${food.target.second})" + "=|", 7)
        return JSONObject(mapOf("X" to food.target.first, "Y" to food.target.second, "Split" to food.split, "Debug" to LOG))


    }

    return JSONObject(mapOf("X" to 0, "Y" to 0, "Debug" to "Died"))


}


//---strategy fun--------------------------------------------------------------------------------------------------
private fun noFoodMoveSpiral(): Answer {
    closerToCenterNoFoodTimer--
    alpha += (Math.PI / rad).toFloat()  //изменение угла.

    if (lastCallNoFoodTick + 1 == ticCount) {
        rad += 0.5
    } else {
        closerToCenterNoFoodTimer = 0
        rad = 40.0
    }

    var x = wConfig!!.GAME_WIDTH / 2F
    var y = wConfig!!.GAME_HEIGHT / 2F

    if (closerToCenterNoFoodTimer <= 0) {
        x = myFrags[0].x
        y = myFrags[0].y

        val closerMoveTime: Int = (myFrags[0].dist(wConfig!!.GAME_WIDTH / 2F, wConfig!!.GAME_HEIGHT / 2F) / (myFrags[0].maxSpeed * 2)).toInt()
        when {
            myFrags[0].x > wConfig!!.GAME_WIDTH - dangetBorderDist -> {
                closerToCenterNoFoodTimer = closerMoveTime
                x = wConfig!!.GAME_WIDTH / 2F
            }
            myFrags[0].x < dangetBorderDist -> {
                closerToCenterNoFoodTimer = closerMoveTime
                x = wConfig!!.GAME_WIDTH / 2F
            }
            myFrags[0].y > wConfig!!.GAME_HEIGHT - dangetBorderDist -> {
                closerToCenterNoFoodTimer = closerMoveTime
                y = wConfig!!.GAME_HEIGHT / 2F
            }
            myFrags[0].y < dangetBorderDist -> {
                closerToCenterNoFoodTimer = closerMoveTime
                y = wConfig!!.GAME_HEIGHT / 2F
            }
        }

    }
    lastCallNoFoodTick = ticCount

    addToLOG("noFoodMoveSpiral ", 1)
    return Answer(comment = "noFoodMoveSpiral ", target = Pair((x + Math.cos(alpha.toDouble()) * 10).toFloat(), (y + Math.sin(alpha.toDouble()) * 10).toFloat()), actionOwner = myFrags[0])
}

private fun noFoodMoveLine(): Answer {


    var x = wConfig!!.GAME_WIDTH / 2F
    var y = wConfig!!.GAME_HEIGHT / 2F



    addToLOG("noFoodMoveSpiral ", 1)
    return Answer(comment = "noFoodMoveSpiral ", target = Pair((x + Math.cos(alpha.toDouble()) * 10).toFloat(), (y + Math.sin(alpha.toDouble()) * 10).toFloat()), actionOwner = myFrags[0])
}


fun eatSweetEnemy(): Answer? {
    var spl: Boolean = false
    var maxHavieVisibleEnemValue: Float = 0F
    var maxMW = 0F

    if (myFrags.size < wConfig!!.MAX_FRAGS_CNT) {
        for (enem in enemyFrags) {
            if (enem.spiritExistTimer == -1 && enem.mass > maxHavieVisibleEnemValue) {
                maxHavieVisibleEnemValue = enem.mass
            }
        }
        for (plPart in myFrags) {
            if (maxMW < plPart.mass) {
                maxMW = plPart.mass
            }

        }


    }

    if (myFrags.size < wConfig!!.MAX_FRAGS_CNT) {
        for (plPart in myFrags) {
            for (enem in enemyFrags) {
                if (enem.spiritExistTimer == -1 && plPart.mass>120 && plPart.mass / 2 > maxHavieVisibleEnemValue && plPart.mass / 2 > enem.mass * 1.2 && plPart.dist(enem)  > distAfterSplit(plPart.speedX, plPart.speedY, enem, plPart)) {
                    spl = true
                    return Answer(comment = "eatSweetEnemy", target = Pair(enem.x + velKaf * enem.speedX, enem.y + velKaf * enem.speedY), actionOwner = plPart, split = (spl))

                }

            }

        }

    }

    var dangeEnemiMinDist: Float = 100000F
    var dangeEnmy:PlUnit?=null
    var dangeEnmyTarget:PlUnit?=null

    var sweatEnemyMinDist: Float = 100000F
    var sweatEnmy:PlUnit?=null
    var sweatEnmyHunter:PlUnit?=null
    for (plPart in myFrags) {
        for (enem in enemyFrags) {
            if (enem.spiritExistTimer == -1 && plPart.mass * 1.2 < enem.mass && plPart.dist(enem) < dangeEnemiMinDist) {
                dangeEnemiMinDist = plPart.dist(enem)
                dangeEnmy=enem
                dangeEnmyTarget=plPart
            }
            if (enem.spiritExistTimer == -1 && plPart.mass > enem.mass * 1.2 && plPart.dist(enem) < sweatEnemyMinDist) {
                sweatEnemyMinDist = plPart.dist(enem)
                sweatEnmy=enem
                sweatEnmyHunter=plPart
            }
        }
    }


    if (sweatEnmy!=null && (dangeEnmy==null || (prSweatDistanse/sweatEnemyMinDist >prDangerDistanse/dangeEnemiMinDist && sweatEnemyMinDist<dangeEnemiMinDist))) {
        prSweatDistanse=sweatEnemyMinDist
        prDangerDistanse=dangeEnemiMinDist

        return Answer(comment = "eatSweetEnemy", target = Pair(sweatEnmy.x + velKaf * sweatEnmy.speedX, sweatEnmy.y + velKaf * sweatEnmy.speedY), actionOwner = sweatEnmyHunter!!, split = (spl && sweatEnmyHunter!!.mass / 2 > sweatEnmy.mass * 1.2))
    }else if (dangeEnmy!=null){
        return runFromDangerEnemyCircle()
    }
    return null
}

fun goToVirus(): Answer? {


    var minWigth = 10000F
    var maxWigth = 0F

    for (plPart in myFrags) {
        minWigth = if (plPart.mass < minWigth) plPart.mass else minWigth
        maxWigth = if (plPart.mass > maxWigth) plPart.mass else maxWigth

    }


    if (maxWigth > 121 && food.size > 4 && lastSawEnemyTimer == 0) {
        return Answer(comment = "goToVirus: ", target = Pair(myFrags[0].x + myFrags[0].speedX, myFrags[0].y + myFrags[0].speedY), actionOwner = myFrags[0], split = true)
    }

    return null
}

//------------------------
fun runFromDangerEnemyCircle(): Answer? {
    var nearestToDengerEnemyMyFrag: PlUnit? = null
    var nearestToMyFragDengerEnemy: PlUnit? = null
    var minDistans: Float = Float.MAX_VALUE

    for (plPart in myFrags) {
        for (enem in enemyFrags) {
            if (plPart.mass * 1.1 < enem.mass && plPart.dist(enem) < minDistans) {//plPart.mass * 1.1 < enem.mass &&
                nearestToDengerEnemyMyFrag = plPart
                nearestToMyFragDengerEnemy = enem
                minDistans = plPart.dist(enem)
            }

        }
    }


    if (minDistans<200 && nearestToDengerEnemyMyFrag != null && nearestToMyFragDengerEnemy != null) {
        lastEnemyWigth = nearestToMyFragDengerEnemy.mass
        if (nearestToDengerEnemyMyFrag.dist(nearestToMyFragDengerEnemy) < 1.1 * (nearestToDengerEnemyMyFrag.radius + nearestToMyFragDengerEnemy.radius)) return runFromDangerEnemyLine()

        var normWay = 0
        var dir = runRotateDir
        var radius = 1.2
        var rotatedXY: Pair<Float, Float>

        while (normWay <= 0) {
            addToLOG(".-.", 5)

            if (radius < 1.09) {

                return runFromDangerEnemyLine()


            }
            val testPlUnit: PlUnit = nearestToDengerEnemyMyFrag.clone()
            val testEnem: PlUnit = nearestToMyFragDengerEnemy.clone()
            normWay = 1

            for (i in 1..5) {// deep must be connected to maxSpeed and Inertion
                rotatedXY = circleMoov(testPlUnit, testEnem, dir, radius)

                val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, rotatedXY)

                val (newEnemSpeedX: Float, newEnemSpeedY: Float) = fetcherSpeedXY(testEnem, Pair(testPlUnit.x + newMySpeedX, testPlUnit.y + newMySpeedY))


                testPlUnit.speedX = newMySpeedX
                testPlUnit.speedY = newMySpeedY

                testPlUnit.x += newMySpeedX
                testPlUnit.y += newMySpeedY


                testEnem.speedX = newEnemSpeedX
                testEnem.speedY = newEnemSpeedY
                testEnem.x += newEnemSpeedX
                testEnem.y += newEnemSpeedY





                if (testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius * 1.5 || testPlUnit.x < testPlUnit.radius * 1.5 || testPlUnit.y >= wConfig!!.GAME_HEIGHT - testPlUnit.radius * 1.5 || testPlUnit.y < testPlUnit.radius * 1.5) {
                    radius += if (runRotateDir == dir) 0.0 else -0.1
                    dir *= -1
                    addToLOG("normWay=-1....dir=$dir..radius=$radius....(${testPlUnit.x}; ${testPlUnit.y})", 5)
                    normWay = -1
                    break
                }



                if (testPlUnit.dist(testEnem) < testEnem.radius) {
                    radius += if (runRotateDir == dir) 0.0 else -0.1
                    dir *= -1
                    addToLOG("normWay=-2....dir=$dir..radius=$radius....(${testPlUnit.x}; ${testPlUnit.y})", 5)
                    normWay = -2
                    break
                }
            }

            //addToLOG(" normWay=1....dir=$dir..radius=$radius....(${testPlUnit.x}; ${testPlUnit.y})", 5)


        }
        runRotateDir = dir
        rotatedXY = circleMoov(nearestToDengerEnemyMyFrag, nearestToMyFragDengerEnemy, dir, radius)
        addToLOG(". runFromDangerEnemyCircle  dir=$dir radius=$radius ", 7)
        return Answer("", false, rotatedXY, nearestToDengerEnemyMyFrag)

    }


    /* while (lastSawEnemyTimer > 0) {
         LOG = "runFromDangerEnemyCircle 10-steps more+"
         return Pair(myFrags[0].x + myFrags[0].speedX * 100, myFrags[0].y + myFrags[0].speedY * 100)
     }*/


    return null
}

fun runFromDangerEnemyLine(): Answer? {
    var nearestToDengerEnemyMyFrag: PlUnit? = null
    var nearestToMyFragDengerEnemy: PlUnit? = null
    var minDistans: Float = Float.MAX_VALUE

    val copyMyFrags: MutableList<PlUnit> = mutableListOf<PlUnit>()
    val copyEnemyFrags: MutableList<PlUnit> = mutableListOf<PlUnit>()

    for (plPart in myFrags) {
        for (enem in enemyFrags) {
            if (plPart.mass * 1.2 < enem.mass && plPart.dist(enem) < minDistans) {//
                nearestToDengerEnemyMyFrag = plPart
                nearestToMyFragDengerEnemy = enem
                minDistans = plPart.dist(enem)
            }
        }
    }

    if (nearestToDengerEnemyMyFrag != null && nearestToMyFragDengerEnemy != null) {
        var stepsCanMove: Int = 0
        val eaten: Boolean = nearestToDengerEnemyMyFrag.dist(nearestToMyFragDengerEnemy) < nearestToMyFragDengerEnemy.radius + nearestToDengerEnemyMyFrag.radius
        var movePoint: Pair<Float, Float>?

        var maxDistans = 0F
        var maxSteps = -1
        var bestDir: Pair<Float, Float>? = null

        for (k in -1..1) {
            // if (maxSteps == 5) break
            for (l in -1..1) {
                //    if (maxSteps == 5) break
                var resetEaten = eaten
                val testPlUnit: PlUnit = nearestToDengerEnemyMyFrag.clone()
                val testEnem: PlUnit = nearestToMyFragDengerEnemy.clone()

                if ((k == 0 && l == 0) ||
                        (k == 0 && ((l == 1 && testPlUnit.dist2(testPlUnit.x, 990F) < 10000) || (l == -1 && testPlUnit.dist2(testPlUnit.x, 0F) < 10000)))
                        ||
                        (l == 0 && ((k == 1 && testPlUnit.dist2(990F, testPlUnit.y) < 10000) || (k == -1 && testPlUnit.dist2(0F, testPlUnit.y) < 10000)))
                ) {
                    continue
                }

                copyMyFrags.clear()
                copyEnemyFrags.clear()
                for (plPart in myFrags) copyMyFrags.add(plPart.clone())
                for (enem in enemyFrags) copyEnemyFrags.add(enem.clone())

                var sumDist = 0F
                var stepDist = 0F
                var minDst = Float.MAX_VALUE
                stepsCanMove = 0
                for (i in 1..10) {// deep must be connected to maxSpeed and Inertion
                    movePoint = strengthMoov(testPlUnit, Pair(k.toFloat(), l.toFloat()))
                    stepDist = 0F

                    var speedVector: Pair<Float, Float>
                    for (copyPlPart in copyMyFrags) {
                        speedVector = fetcherSpeedXY(copyPlPart, movePoint)

                        when {
                            copyPlPart.x + speedVector.first < copyPlPart.radius -> {
                                speedVector = Pair(0F, speedVector.second)
                                copyPlPart.x = copyPlPart.radius
                                stepsCanMove -= 1
                            }
                            copyPlPart.x + speedVector.first > wConfig!!.GAME_WIDTH - copyPlPart.radius -> {
                                speedVector = Pair(0F, speedVector.second)
                                copyPlPart.x = wConfig!!.GAME_WIDTH.toFloat() - copyPlPart.radius
                                stepsCanMove -= 1
                            }
                            copyPlPart.y + speedVector.second < copyPlPart.radius -> {
                                speedVector = Pair(speedVector.first, 0F)
                                copyPlPart.y = copyPlPart.radius
                                stepsCanMove -= 1
                            }
                            copyPlPart.y + speedVector.second > wConfig!!.GAME_HEIGHT - copyPlPart.radius -> {
                                speedVector = Pair(speedVector.first, 0F)
                                copyPlPart.y = wConfig!!.GAME_HEIGHT.toFloat() - copyPlPart.radius
                                stepsCanMove -= 1
                            }

                        }
                        copyPlPart.speedX = speedVector.first
                        copyPlPart.speedY = speedVector.second
                        copyPlPart.x += speedVector.first
                        copyPlPart.y += speedVector.second
                    }

                    /* for (copyEnem in copyEnemyFrags) {
                         speedVector = fetcherSpeedXY(copyEnem, Pair(testPlUnit.x, testPlUnit.y))//fetcherSpeedXY(copyPlPart, movePoint)
                         copyEnem.speedX = speedVector.first
                         copyEnem.speedY = speedVector.second
                         copyEnem.x += speedVector.first
                         copyEnem.y += speedVector.second
                     }
                     var (newEnemSpeedX: Float, newEnemSpeedY: Float) = fetcherSpeedXY(testEnem, Pair(testPlUnit.x, testPlUnit.y))//enemi start speed unnown
                     testEnem.speedX = newEnemSpeedX
                     testEnem.speedY = newEnemSpeedY
                     testEnem.x += newEnemSpeedX
                     testEnem.y += newEnemSpeedY*/

                    speedVector = fetcherSpeedXY(testPlUnit, movePoint)
                    when {
                        testPlUnit.x + speedVector.first < testPlUnit.radius -> {
                            speedVector = Pair(0F, speedVector.second)
                            testPlUnit.x = testPlUnit.radius
                            stepsCanMove -= 1
                        }
                        testPlUnit.x + speedVector.first > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                            speedVector = Pair(0F, speedVector.second)
                            testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                            stepsCanMove -= 1
                        }
                        testPlUnit.y + speedVector.second < testPlUnit.radius -> {
                            speedVector = Pair(speedVector.first, 0F)
                            testPlUnit.y = testPlUnit.radius
                            stepsCanMove -= 1
                        }
                        testPlUnit.y + speedVector.second > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                            speedVector = Pair(speedVector.first, 0F)
                            testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() - testPlUnit.radius
                            stepsCanMove -= 1
                        }
                    }
                    testPlUnit.speedX = speedVector.first
                    testPlUnit.speedY = speedVector.second
                    testPlUnit.x += speedVector.first
                    testPlUnit.y += speedVector.second


                    if (testEnem.dist2(testPlUnit) < 0.9 * (testEnem.radius + testPlUnit.radius) * (testEnem.radius + testPlUnit.radius)) {
                        if (!resetEaten) {
                            stepsCanMove -= 10 / i
                            resetEaten = true
                        }

                    }


                    stepsCanMove += 3

                    for (mP in copyMyFrags) {
                        for (eP in copyEnemyFrags) {
                            if (mP.mass * 1.2 < eP.mass) {
                                if (minDst > mP.dist(eP)) {
                                    minDst = mP.dist(eP)
                                }
                                stepDist += mP.dist(eP)
                            }
                        }
                    }
                    sumDist += stepDist * minDst / i
                    //  addToLOG("($k; $l) i=$i    steps=<$stepsCanMove><><${testPlUnit.dist(testEnem)}>", 7)

                }


                /* for (mP in copyMyFrags) {
                     for (eP in copyEnemyFrags) {
                         if (mP.mass * 1.2 < eP.mass) {
                             sumDist += mP.dist(eP)
                         }
                     }
                 }
                 sumDist *= minDst*/

                addToLOG(" stepsCanMove=$stepsCanMove  ($k; $l)  <$maxDistans><$sumDist><(${testPlUnit.x},${testPlUnit.y})>", 7)


                if (maxSteps * maxDistans < stepsCanMove * sumDist) {
                    maxSteps = stepsCanMove
                    maxDistans = sumDist
                    bestDir = Pair(k.toFloat(), l.toFloat())
                }


            }
        }



        return if (bestDir == null) null else Answer(comment = "runLine", target = strengthMoov(nearestToDengerEnemyMyFrag, bestDir), actionOwner = nearestToDengerEnemyMyFrag)

    }

    return null
}

fun findFastedGetFood(): Answer? {
    var fastedGetFoodId: Int = -1
    var fasterPlUnit: PlUnit = myFrags[0]
    var minTicksToGet: Double = Double.MAX_VALUE
    for (plPart in myFrags) { //take cure about self parts collision, black hole collision
        for ((i, foodObj) in food.withIndex()) {
            val testPlUnit: PlUnit = plPart.clone()
            var ticsToGet = 0.0
            var lSVector: Pair<Float, Float>
            while (!testPlUnit.canEat(foodObj)) {
                ticsToGet++
                lSVector = getSpeedVectorToTargetPoint(Pair(foodObj.x, foodObj.y), testPlUnit)
                val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, Pair(testPlUnit.x - velKaf * lSVector.first, testPlUnit.y - velKaf * lSVector.second))

                // addToLOG(" (${testPlUnit.x} ; ${testPlUnit.y})-(${movePoint.first} ; ${movePoint.second})-(${k} ; ${l})->($newMySpeedX ;$newMySpeedY)\\> ", 1)

                testPlUnit.x += newMySpeedX
                testPlUnit.y += newMySpeedY

                when {
                    testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                        testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                    }
                    testPlUnit.x < testPlUnit.radius -> {
                        testPlUnit.x = testPlUnit.radius
                    }
                    testPlUnit.y > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                        testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() + testPlUnit.radius
                    }
                    testPlUnit.y < testPlUnit.radius -> {
                        testPlUnit.y = testPlUnit.radius
                    }
                }

                testPlUnit.speedX = newMySpeedX
                testPlUnit.speedY = newMySpeedY


            }
            for (foodObj1 in food) {
                while (!testPlUnit.canEat(foodObj1)) {
                    ticsToGet += 0.5
                    lSVector = getSpeedVectorToTargetPoint(Pair(foodObj1.x, foodObj1.y), testPlUnit)
                    val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, Pair(testPlUnit.x - velKaf * lSVector.first, testPlUnit.y - velKaf * lSVector.second))

                    testPlUnit.x += newMySpeedX
                    testPlUnit.y += newMySpeedY

                    when {
                        testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                            testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                        }
                        testPlUnit.x < testPlUnit.radius -> {
                            testPlUnit.x = testPlUnit.radius
                        }
                        testPlUnit.y > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                            testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() + testPlUnit.radius
                        }
                        testPlUnit.y < testPlUnit.radius -> {
                            testPlUnit.y = testPlUnit.radius
                        }
                    }

                    testPlUnit.speedX = newMySpeedX
                    testPlUnit.speedY = newMySpeedY


                }
                for (foodObj2 in food) {
                    while (!testPlUnit.canEat(foodObj2)) {
                        ticsToGet += 0.25
                        lSVector = getSpeedVectorToTargetPoint(Pair(foodObj2.x, foodObj2.y), testPlUnit)
                        val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, Pair(testPlUnit.x - velKaf * lSVector.first, testPlUnit.y - velKaf * lSVector.second))

                        testPlUnit.x += newMySpeedX
                        testPlUnit.y += newMySpeedY

                        when {
                            testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                                testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                            }
                            testPlUnit.x < testPlUnit.radius -> {
                                testPlUnit.x = testPlUnit.radius
                            }
                            testPlUnit.y > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                                testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() + testPlUnit.radius
                            }
                            testPlUnit.y < testPlUnit.radius -> {
                                testPlUnit.y = testPlUnit.radius
                            }
                        }

                        testPlUnit.speedX = newMySpeedX
                        testPlUnit.speedY = newMySpeedY


                    }


                }
            }

            if (minTicksToGet > ticsToGet) {
                minTicksToGet = ticsToGet
                fastedGetFoodId = i
                fasterPlUnit = plPart

            }
        }
    }
    addToLOG("findFastedGetFood : $fastedGetFoodId", 1)
    return if (fastedGetFoodId != -1) Answer(target = Pair(food[fastedGetFoodId].x, food[fastedGetFoodId].y), actionOwner = fasterPlUnit) else null
}

fun findFastedGetFood3vel(): Answer? {
    var fastedGetFoodId: Int = -1
    var fasterPlUnit: PlUnit = myFrags[0]
    var minTicksToGet: Double = Double.MAX_VALUE
    // val copyFood: MutableList<Food> = mutableListOf<Food>()
    //  for (f in copyFood) copyFood.add(f.clone())

    var ticsToGet = 0.0
    var testPlUnit: PlUnit


    for (plPart in myFrags) { //take cure about self parts collision, black hole collision
        for ((i, foodObj) in food.withIndex()) {
            testPlUnit = plPart.clone()
            ticsToGet = 0.0
            // var lSVector: Pair<Float, Float>
            while (!testPlUnit.canEat(foodObj)) {
                ticsToGet++
                if (ticsToGet > 100) break
                val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, Pair(testPlUnit.x - velKaf * testPlUnit.speedX, testPlUnit.y - velKaf * testPlUnit.speedY))

                // addToLOG(" (${testPlUnit.x} ; ${testPlUnit.y})-(${movePoint.first} ; ${movePoint.second})-(${k} ; ${l})->($newMySpeedX ;$newMySpeedY)\\> ", 1)

                testPlUnit.x += newMySpeedX
                testPlUnit.y += newMySpeedY

                when {
                    testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                        testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                    }
                    testPlUnit.x < testPlUnit.radius -> {
                        testPlUnit.x = testPlUnit.radius
                    }
                    testPlUnit.y > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                        testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() + testPlUnit.radius
                    }
                    testPlUnit.y < testPlUnit.radius -> {
                        testPlUnit.y = testPlUnit.radius
                    }
                }

                testPlUnit.speedX = newMySpeedX
                testPlUnit.speedY = newMySpeedY


            }

            if (ticsToGet > 1000) break

            for (foodObj1 in food) {
                if (foodObj != foodObj1)
                    while (!testPlUnit.canEat(foodObj1)) {
                        ticsToGet += 0.5
                        if (ticsToGet > 150) break
                        val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, Pair(testPlUnit.x - velKaf * testPlUnit.speedX, testPlUnit.y - velKaf * testPlUnit.speedY))

                        testPlUnit.x += newMySpeedX
                        testPlUnit.y += newMySpeedY

                        when {
                            testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                                testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                            }
                            testPlUnit.x < testPlUnit.radius -> {
                                testPlUnit.x = testPlUnit.radius
                            }
                            testPlUnit.y > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                                testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() + testPlUnit.radius
                            }
                            testPlUnit.y < testPlUnit.radius -> {
                                testPlUnit.y = testPlUnit.radius
                            }
                        }

                        testPlUnit.speedX = newMySpeedX
                        testPlUnit.speedY = newMySpeedY


                    }
                if (ticsToGet > 1500) break
                for (foodObj2 in food) {
                    if (foodObj != foodObj1 && foodObj != foodObj2 && foodObj1 != foodObj2)
                        while (!testPlUnit.canEat(foodObj2)) {
                            ticsToGet += 0.25
                            if (ticsToGet > 175) break
                            val (newMySpeedX: Float, newMySpeedY: Float) = fetcherSpeedXY(testPlUnit, Pair(testPlUnit.x - velKaf * testPlUnit.speedX, testPlUnit.y - velKaf * testPlUnit.speedY))

                            testPlUnit.x += newMySpeedX
                            testPlUnit.y += newMySpeedY

                            when {
                                testPlUnit.x > wConfig!!.GAME_WIDTH - testPlUnit.radius -> {
                                    testPlUnit.x = wConfig!!.GAME_WIDTH.toFloat() - testPlUnit.radius
                                }
                                testPlUnit.x < testPlUnit.radius -> {
                                    testPlUnit.x = testPlUnit.radius
                                }
                                testPlUnit.y > wConfig!!.GAME_HEIGHT - testPlUnit.radius -> {
                                    testPlUnit.y = wConfig!!.GAME_HEIGHT.toFloat() + testPlUnit.radius
                                }
                                testPlUnit.y < testPlUnit.radius -> {
                                    testPlUnit.y = testPlUnit.radius
                                }
                            }

                            testPlUnit.speedX = newMySpeedX
                            testPlUnit.speedY = newMySpeedY


                        }


                }
                if (ticsToGet > 1750) break
            }

            if (minTicksToGet > ticsToGet && ticsToGet < 1500) {
                minTicksToGet = ticsToGet
                fastedGetFoodId = i
                fasterPlUnit = plPart

            }
        }
    }
    addToLOG("findFastedGetFood3vel : $fastedGetFoodId", 1)
    return if (fastedGetFoodId != -1) Answer(comment = "findFastedGetFood3vel", target = Pair(food[fastedGetFoodId].x - velKaf * fasterPlUnit.speedX, food[fastedGetFoodId].y - velKaf * fasterPlUnit.speedY), actionOwner = fasterPlUnit) else null
}

fun findNearestFood(): Answer? {
    var nearestFoodId: Int = -1
    var nearesToFoodPart: PlUnit = myFrags[0]
    var minDist: Float = Float.POSITIVE_INFINITY
    for (plPart in myFrags) {
        for ((i, food) in food.withIndex()) {
            if (minDist > plPart.dist2(food)) {
                minDist = plPart.dist2(food)
                nearestFoodId = i
                nearesToFoodPart = plPart

            }
        }
    }
    addToLOG("END findFood $nearestFoodId", 1)
    return if (nearestFoodId != -1) Answer(comment = "findNearestFood:", target = Pair(food[nearestFoodId].x - velKaf * nearesToFoodPart.speedX, food[nearestFoodId].y - velKaf * nearesToFoodPart.speedY), actionOwner = nearesToFoodPart) else null
}

fun noFoodCirclMove(plPart: PlUnit, center: Pair<Float, Float>, rotateDir: Int = 1, rKof: Double = 1.0): Answer {

    val rotatedX: Float
    val rotatedY: Float

    val d = 1 * Math.PI / 2
    rotatedX = ((rKof * Math.cos(d) * (plPart.x - center.first) + rKof * Math.sin(d) * (-plPart.y + center.second)) + center.first).toFloat()
    rotatedY = ((rKof * Math.cos(d) * (plPart.y - center.second) + rKof * Math.sin(d) * (plPart.x - center.first)) + center.second).toFloat()

    return Answer(target = Pair(rotatedX, rotatedY), comment = "noFoodCirclMove", actionOwner = plPart)
}


//---simulation  fun-------------------------------------------------------------------------------------------------

fun distAfterSplit(vectorFirst: Float, vectorSecond: Float, enem: PlUnit, plPart: PlUnit): Float {
    val vectorLength: Float = Math.sqrt((vectorFirst * vectorFirst + vectorSecond * vectorSecond).toDouble()).toFloat()
    val nSX = vectorFirst * vectorFirst / (vectorLength * vectorLength * 64)
    val nSY = vectorSecond * vectorSecond / (vectorLength * vectorLength * 64)

    return enem.dist(plPart.x + nSX / (2 * wConfig!!.VISCOSITY), plPart.y + nSY / (2 * wConfig!!.VISCOSITY))

}

fun fetcherSpeedXY(selfUnit: PlUnit, moveDir: Pair<Float, Float>): Pair<Float, Float> {
    val vector: Pair<Float, Float> = Pair(moveDir.first - selfUnit.x, moveDir.second - selfUnit.y)
    //addToLOG("=====  v=(${vector.first};${vector.second} "
    val vectorLength: Float = Math.sqrt((vector.first * vector.first + vector.second * vector.second).toDouble()).toFloat()
    // addToLOG(" vL=($vectorLength )"
    val normDirVector: Pair<Float, Float> = Pair(vector.first / vectorLength, vector.second / vectorLength)
    // addToLOG(" nDV=(${normDirVector.first};${normDirVector.second}) "
    // addToLOG("  PARAM =(${selfUnit.speedX};${selfUnit.maxSpeed};${wConfig!!.INERTION_FACTOR} ;${selfUnit.mass} ====== "

    val newSpeedX = selfUnit.speedX + (normDirVector.first * selfUnit.maxSpeed - selfUnit.speedX) * wConfig!!.INERTION_FACTOR / selfUnit.mass
    val newSpeedY = selfUnit.speedY + (normDirVector.second * selfUnit.maxSpeed - selfUnit.speedY) * wConfig!!.INERTION_FACTOR / selfUnit.mass


    return Pair((newSpeedX).toFloat(), (newSpeedY).toFloat())

}

fun getSpeedVectorToTargetPoint(food: Pair<Float, Float>, plPart: PlUnit): Pair<Float, Float> {
    val inert = wConfig!!.INERTION_FACTOR / plPart.mass
    val nx = (food.first - plPart.x - plPart.speedX + plPart.speedX * inert) / plPart.maxSpeed * inert
    val ny = (food.second - plPart.y - plPart.speedY + plPart.speedY * inert) / plPart.maxSpeed * inert

    return return Pair(nx.toFloat() * 1000, ny.toFloat() * 1000)
}

fun getFinalTargetPoint(food: Pair<Float, Float>, plPart: PlUnit): Pair<Float, Float> {
    val inert = wConfig!!.INERTION_FACTOR / plPart.mass
    val nx = (food.first - plPart.x - plPart.speedX + plPart.speedX * inert) / plPart.maxSpeed * inert
    val ny = (food.second - plPart.y - plPart.speedY + plPart.speedY * inert) / plPart.maxSpeed * inert


    val aStart = Pair(plPart.x, plPart.y)
    val aEnd = Pair((plPart.x + nx.toFloat() * 100000), (plPart.y + ny.toFloat() * 100000))

    var targP = twoLinesCrossPoint(aStart, aEnd, Pair(0F, 0F), Pair(wConfig!!.GAME_WIDTH.toFloat(), 0f))

    val border = 5
    if (targP != null && targP.first >= -border && targP.first <= wConfig!!.GAME_WIDTH.toFloat() + border && targP.second >= -border && targP.second <= wConfig!!.GAME_HEIGHT.toFloat() + border && (targP.first > aStart.first) == (aEnd.first > aStart.first) && (targP.second > aStart.second) == (aEnd.second > aStart.second)) {
        return targP
    } else {
        targP = twoLinesCrossPoint(aStart, aEnd, Pair(0F, 0F), Pair(0F, wConfig!!.GAME_HEIGHT.toFloat()))
        if (targP != null && targP.first >= -border && targP.first <= wConfig!!.GAME_WIDTH.toFloat() + border && targP.second >= -border && targP.second <= wConfig!!.GAME_HEIGHT.toFloat() + border && (targP.first > aStart.first) == (aEnd.first > aStart.first) && (targP.second > aStart.second) == (aEnd.second > aStart.second)) {
            return targP
        } else {
            targP = twoLinesCrossPoint(aStart, aEnd, Pair(wConfig!!.GAME_WIDTH.toFloat(), wConfig!!.GAME_HEIGHT.toFloat()), Pair(wConfig!!.GAME_WIDTH.toFloat(), 0F))
             if (targP != null && targP.first >= -border && targP.first <= wConfig!!.GAME_WIDTH.toFloat() + border && targP.second >= -border && targP.second <= wConfig!!.GAME_HEIGHT.toFloat() + border && (targP.first > aStart.first) == (aEnd.first > aStart.first) && (targP.second > aStart.second) == (aEnd.second > aStart.second)) {
                return targP
            } else {
                targP = twoLinesCrossPoint(aStart, aEnd, Pair(wConfig!!.GAME_WIDTH.toFloat(), wConfig!!.GAME_HEIGHT.toFloat()), Pair(0F, wConfig!!.GAME_HEIGHT.toFloat()))
                if (targP != null && targP.first >= -border && targP.first <= wConfig!!.GAME_WIDTH.toFloat() + border && targP.second >= -border && targP.second <= wConfig!!.GAME_HEIGHT.toFloat() + border && (targP.first > aStart.first) == (aEnd.first > aStart.first) && (targP.second > aStart.second) == (aEnd.second > aStart.second)) {
                    return targP
                } else {

                    addToLOG("start-(${aStart.first};${aStart.second}) end-(${aEnd.first};${aEnd.second})", 7)
                    return aEnd
                }
            }
        }
    }

}


//---helpher fun-------------------------------------------------------------------------------------------------


fun BufferedWriter.writeLn(line: String) {
    this.write(line)
    this.newLine()
}

fun addToLOG(line: String, cod: Int) {
    if (cod == 7) {
        LOG += line
    }
}

fun sendToFile() {
    if (ticCount == 800) {
        File("C:\\mail.group\\mini201803\\out\\artifacts\\mini201803_jar\\log.txt").bufferedWriter().use { out ->
            out.writeLn(LOG)
        }
    }
}


fun pointToLineDistance(pA: Point, pB: Point, pO: Point) = Math.abs(((pB.x - pA.x) * (pO.y - pA.y) - (pB.y - pA.y) * (pO.x - pA.x)) / Math.sqrt(Math.pow((pB.x - pA.x).toDouble(), 2.0) + Math.pow((pB.y - pA.y).toDouble(), 2.0)))

fun twoLinesCrossPoint(aStart: Pair<Float, Float>, aEnd: Pair<Float, Float>, bStart: Pair<Float, Float>, bEnd: Pair<Float, Float>): Pair<Float, Float>? {

    val a1 = aStart.second - aEnd.second
    val b1 = aEnd.first - aStart.first
    val a2 = bStart.second - bEnd.second
    val b2 = bEnd.first - bStart.first


    val d = a1 * b2 - a2 * b1
    if (d != 0F) {
        val c1 = aEnd.second * aStart.first - aEnd.first * aStart.second
        val c2 = bEnd.second * bStart.first - bEnd.first * bStart.second

        val xi = (b1 * c2 - b2 * c1) / d
        val yi = (a2 * c1 - a1 * c2) / d
        return Pair(xi, yi)
    } else {
        return null
    }
}

fun circleMoov(plPart: PlUnit, enem: PlUnit, rotateDir: Int = 1, rKof: Double = 1.1): Pair<Float, Float> {

    val rotatedX: Float
    val rotatedY: Float

    val d = rotateDir * enemyRunAlpha
    rotatedX = ((rKof * Math.cos(d) * (plPart.x - enem.x) + rKof * Math.sin(d) * (plPart.y - enem.y)) + enem.x).toFloat()
    rotatedY = ((rKof * Math.cos(d) * (plPart.y - enem.y) - rKof * Math.sin(d) * (plPart.x - enem.x)) + enem.y).toFloat()

    return Pair(rotatedX, rotatedY)
}

fun strengthMoov(plPart: PlUnit, dirXY: Pair<Float, Float>): Pair<Float, Float> {

    val gotoX: Float
    val gotoY: Float

    gotoX = plPart.x + dirXY.first * 100
    gotoY = plPart.y + dirXY.second * 100
    return Pair(gotoX, gotoY)
}

//---class objects-------------------------------------------------------------------------------------------------
data class Answer(var comment: String = "", var split: Boolean = false, var target: Pair<Float, Float>, var actionOwner: PlUnit = myFrags[0])

data class WorldConst(val GAME_WIDTH: Int = 0,
                      val GAME_HEIGHT: Int = 0,
                      val GAME_TICKS: Int = 0,
                      val FOOD_MASS: Float = 0F,
                      val MAX_FRAGS_CNT: Int = 0,
                      val TICKS_TIL_FUSION: Int = 0,
                      val VIRUS_RADIUS: Float = 0F,
                      val VIRUS_SPLIT_MASS: Float = 0F,
                      val VISCOSITY: Float = 0F,
                      val INERTION_FACTOR: Float = 0F,
                      val SPEED_FACTOR: Float = 0F,
                      val MASS_EAT_FACTOR: Double = 1.2,
                      val DIAM_EAT_FACTOR: Double = 2.0 / 3.0)

abstract class Ameba(var id: Double, var x: Float, var y: Float, var radius: Float, var mass: Float) {

    abstract fun getType(): CircleType

    fun dist(fromX: Float, fromY: Float): Float {
        val dx: Double = (x - fromX).toDouble()
        val dy: Double = (y - fromY).toDouble()
        return Math.sqrt((dx * dx + dy * dy)).toFloat()

    }

    fun dist(anotherCircle: Ameba): Float {
        val dx: Double = (x - anotherCircle.x).toDouble()
        val dy: Double = (y - anotherCircle.y).toDouble()
        return Math.sqrt(dx * dx + dy * dy).toFloat()

    }

    fun dist2(fromX: Float, fromY: Float): Float {
        val dx = x - fromX
        val dy = y - fromY
        return (dx * dx + dy * dy)

    }

    fun dist2(anotherCircle: Ameba): Float {
        val dx = x - anotherCircle.x
        val dy = y - anotherCircle.y
        return (dx * dx + dy * dy)

    }


}

class Virus(id: Double = -1.0, x: Float = -1F, y: Float = -1F, radius: Float = wConfig?.VIRUS_RADIUS!!, mass: Float = 40F) : Ameba(id, x, y, radius, mass) {
    init {
        val virusSplitMass = wConfig!!.VIRUS_SPLIT_MASS
    }

    override fun getType(): CircleType {
        return CircleType.VIRUS
    }

    fun updateFromJson(jObj: JSONObject): Unit {

        id = castToDouble(jObj["Id"])
        x = castToFloat(jObj["X"])
        y = castToFloat(jObj["Y"])
        mass = castToFloat(jObj["M"])


    }


}

class PlUnit(id: Double = -1.0, x: Float = -1F, y: Float = -1F, radius: Float = 2.5F, mass: Float = wConfig!!.FOOD_MASS, var speedX: Float = 0F, var speedY: Float = 0F) : Ameba(id, x, y, radius, mass) {
    var maxSpeed: Double = 0.0
    var fuseTimer = 0.0F
    var spiritExistTimer: Int = 0

    init {
        maxSpeed = wConfig!!.SPEED_FACTOR / Math.sqrt(mass.toDouble())

    }

    constructor(self: PlUnit) : this() {
        id = self.id
        x = self.x
        y = self.y
        mass = self.mass
        radius = self.radius
        maxSpeed = wConfig!!.SPEED_FACTOR / Math.sqrt(self.mass.toDouble())
        speedX = self.speedX
        speedY = self.speedY
        fuseTimer = self.fuseTimer

    }


    override fun getType(): CircleType {
        return CircleType.PLAYER
    }

    fun updateFromJson(mine: Boolean = false, jObj: JSONObject): Unit {

        id = castToDouble(jObj["Id"])
        x = castToFloat(jObj["X"])
        y = castToFloat(jObj["Y"])
        mass = castToFloat(jObj["M"])
        radius = castToFloat(jObj["R"])
        maxSpeed = wConfig!!.SPEED_FACTOR / Math.sqrt(mass.toDouble())
        if (mine) {
            speedX = castToFloat(jObj["SX"])
            speedY = castToFloat(jObj["SY"])

            fuseTimer = if (jObj.has("TTF")) castToFloat(jObj["TTF"]) else 0F

        }

    }


    fun canEat(target: Ameba): Boolean {
        if (target.getType() == CircleType.PLAYER && target.id == id) {
            return false
        }
        if (mass > target.mass * wConfig!!.MASS_EAT_FACTOR) { // eat anything
            val dist = target.dist(x, y)
            if (dist - target.radius + (target.radius * 2) * wConfig!!.DIAM_EAT_FACTOR < radius) {
                return (true)
            }
        }
        return false
    }

    fun clone(): PlUnit {

        return PlUnit(this)
    }


}

class Food(id: Double = -1.0, x: Float = -1F, y: Float = -1F, radius: Float = 2.5F, mass: Float = wConfig!!.FOOD_MASS) : Ameba(id, x, y, radius, mass) {
    override fun getType(): CircleType {
        return CircleType.FOOD
    }

    constructor(self: Food) : this() {

        x = self.x
        y = self.y


    }

    fun clone(): Food {

        return Food(this)
    }

    fun updateFromJson(mine: Boolean = false, jObj: JSONObject): Unit {

        x = castToFloat(jObj["X"])
        y = castToFloat(jObj["Y"])

    }
}
