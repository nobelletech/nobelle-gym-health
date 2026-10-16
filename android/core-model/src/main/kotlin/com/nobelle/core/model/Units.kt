package com.nobelle.core.model

/** Explicit, tested unit conversions (§4). */
object Units {
    fun kgToLb(kg: Double) = kg * 2.2046226218
    fun lbToKg(lb: Double) = lb / 2.2046226218
    fun kmToMi(km: Double) = km * 0.6213711922
    fun miToKm(mi: Double) = mi / 0.6213711922
    fun mmolLToMgDl(mmol: Double) = mmol * 18.0182
    fun mgDlToMmolL(mgdl: Double) = mgdl / 18.0182
}
