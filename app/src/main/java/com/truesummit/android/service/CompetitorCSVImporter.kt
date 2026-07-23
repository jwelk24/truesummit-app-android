package com.truesummit.android.service

import java.math.BigDecimal

/**
 * Detects a Mint / YNAB / Monarch CSV export and transcodes it into Summit's
 * generic date,merchant,amount,account,category,memo format.
 * Returns null for unrecognized input so the caller can fall back to a generic importer.
 */
object CompetitorCSVImporter {

    fun transcodeIfKnown(content: String): String? {
        val normalized = content
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val lines = normalized.split("\n").filter { it.isNotBlank() }
        if (lines.size <= 1) return null

        val header = parseLine(lines[0]).map { it.lowercase() }
        fun idx(name: String) = header.indexOf(name).takeIf { it >= 0 }

        val isYNAB = idx("outflow") != null && idx("inflow") != null && idx("payee") != null
        val isMint = idx("transaction type") != null && idx("description") != null && idx("amount") != null
        val isMonarch = idx("merchant") != null && idx("amount") != null &&
            idx("category") != null && idx("account") != null && idx("transaction type") == null

        if (!isYNAB && !isMint && !isMonarch) return null

        val out = mutableListOf("date,merchant,amount,account,category,memo")
        for (line in lines.drop(1)) {
            val f = parseLine(line)
            fun at(i: Int?) = if (i != null && i < f.size) f[i] else ""

            val date: String
            val merchant: String
            val amount: String
            val account: String
            val category: String
            val memo: String

            when {
                isYNAB -> {
                    date = at(idx("date"))
                    merchant = at(idx("payee"))
                    val inflow = parseNumber(at(idx("inflow")))
                    val outflow = parseNumber(at(idx("outflow")))
                    amount = inflow.subtract(outflow).toPlainString()
                    account = at(idx("account"))
                    category = at(idx("category"))
                    memo = at(idx("memo"))
                }
                isMint -> {
                    date = at(idx("date"))
                    merchant = at(idx("description"))
                    val magnitude = parseNumber(at(idx("amount")))
                    val isCredit = at(idx("transaction type")).lowercase() == "credit"
                    amount = (if (isCredit) magnitude else magnitude.negate()).toPlainString()
                    account = at(idx("account name"))
                    category = at(idx("category"))
                    memo = at(idx("notes"))
                }
                else -> { // Monarch
                    date = at(idx("date"))
                    merchant = at(idx("merchant"))
                    amount = parseNumber(at(idx("amount"))).toPlainString()
                    account = at(idx("account"))
                    category = at(idx("category"))
                    memo = at(idx("notes"))
                }
            }

            if (date.isEmpty() || amount.isEmpty()) continue
            out.add(listOf(date, merchant, amount, account, category, memo).joinToString(",") { escape(it) })
        }
        return if (out.size > 1) out.joinToString("\n") else null
    }

    private fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        val chars = line.toCharArray()
        while (i < chars.size) {
            val c = chars[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < chars.size && chars[i + 1] == '"') { current.append('"'); i += 2 }
                        else { inQuotes = false; i++ }
                    } else { current.append(c); i++ }
                }
                c == '"' -> { inQuotes = true; i++ }
                c == ',' -> { fields.add(current.toString().trim()); current.clear(); i++ }
                else -> { current.append(c); i++ }
            }
        }
        fields.add(current.toString().trim())
        return fields
    }

    private fun parseNumber(s: String): BigDecimal {
        val cleaned = s.replace("$", "").replace(",", "").trim()
        return cleaned.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    private fun escape(v: String): String {
        return if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            '"' + v.replace("\"", "\"\"") + '"'
        } else v
    }
}
