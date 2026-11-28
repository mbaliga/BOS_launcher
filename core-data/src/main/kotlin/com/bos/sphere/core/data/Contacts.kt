package com.bos.sphere.core.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

data class ContactHit(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
)

/** Ways to reach a contact. */
enum class Channel { Call, Sms, Email, WhatsApp, Telegram, Signal }

/** Pure helpers, testable on a JVM. */
object ContactLogic {
    /** Digits only (no '+', spaces, dashes); wa.me wants the full international number without '+'. */
    fun digits(number: String): String = number.filter { it.isDigit() }

    fun hasUsableNumber(number: String): Boolean = digits(number).length >= 5

    fun channels(
        hasPhone: Boolean, hasEmail: Boolean,
        whatsApp: Boolean, telegram: Boolean, signal: Boolean,
    ): List<Channel> = buildList {
        if (hasPhone) { add(Channel.Call); add(Channel.Sms) }
        if (hasEmail) add(Channel.Email)
        if (hasPhone && whatsApp) add(Channel.WhatsApp)
        if (hasPhone && telegram) add(Channel.Telegram)
        if (hasPhone && signal) add(Channel.Signal)
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }
}

/** Intent builders. Dial (not call) so no CALL_PHONE permission is needed. */
object ContactActions {
    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_TELEGRAM = "org.telegram.messenger"
    const val PKG_SIGNAL = "org.thoughtcrime.securesms"

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun call(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun sms(number: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun email(address: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun whatsapp(number: String): Intent = view("https://wa.me/${ContactLogic.digits(number)}")
    fun telegram(number: String): Intent = view("https://t.me/+${ContactLogic.digits(number)}")
    fun signal(number: String): Intent = view("https://signal.me/#p/+${ContactLogic.digits(number)}")
    fun openContact(hit: ContactHit): Intent =
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(hit.id, hit.lookupKey)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Throwable) {
        false
    }

    fun availableChannels(context: Context, hit: ContactHit): List<Channel> {
        val usable = hit.phones.any { ContactLogic.hasUsableNumber(it) }
        return ContactLogic.channels(
            hasPhone = hit.phones.isNotEmpty(), hasEmail = hit.emails.isNotEmpty(),
            whatsApp = usable && installed(context, PKG_WHATSAPP),
            telegram = usable && installed(context, PKG_TELEGRAM),
            signal = usable && installed(context, PKG_SIGNAL),
        )
    }

    /** The intent for [channel] on [hit], using the first phone/email. Null if it does not apply. */
    fun intentFor(channel: Channel, hit: ContactHit): Intent? {
        val phone = hit.phones.firstOrNull()
        return when (channel) {
            Channel.Call -> phone?.let(::call)
            Channel.Sms -> phone?.let(::sms)
            Channel.Email -> hit.emails.firstOrNull()?.let(::email)
            Channel.WhatsApp -> phone?.let(::whatsapp)
            Channel.Telegram -> phone?.let(::telegram)
            Channel.Signal -> phone?.let(::signal)
        }
    }
}

/** Contacts lookup via ContactsContract. Call from a background dispatcher. */
class ContactSource(context: Context) {
    private val app = context.applicationContext

    fun hasPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Starred and recently contacted people, for the browse view before anything is typed. */
    fun recent(limit: Int = 12): List<ContactHit> = load(
        ContactsContract.Contacts.CONTENT_URI,
        // Only people you actually have a history with — never just the alphabet.
        "${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1 AND (${ContactsContract.Contacts.STARRED}=1 OR ${ContactsContract.Contacts.LAST_TIME_CONTACTED}>0)",
        "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC, ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        limit,
    )

    /** These specific contacts (the ones you reached through Radar), most recent first as given. */
    fun byIds(ids: List<Long>): List<ContactHit> {
        if (ids.isEmpty()) return emptyList()
        val found = load(
            ContactsContract.Contacts.CONTENT_URI, "${ContactsContract.Contacts._ID} IN (${ids.joinToString(",")})",
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC", ids.size,
        )
        val order = ids.withIndex().associate { it.value to it.index }
        return found.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    fun search(query: String, limit: Int = 6): List<ContactHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return load(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q)), null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC", limit,
        )
    }

    private fun load(uri: Uri, selection: String?, sort: String, limit: Int): List<ContactHit> {
        if (!hasPermission()) return emptyList()
        return try {
            val base = ArrayList<Triple<Long, String, String>>()
            app.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                selection, null, sort,
            )?.use { c ->
                while (c.moveToNext() && base.size < limit) {
                    val name = c.getString(2) ?: continue
                    base += Triple(c.getLong(0), c.getString(1) ?: continue, name)
                }
            }
            if (base.isEmpty()) return emptyList()
            val ids = base.map { it.first }
            val phones = multi(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ids,
            )
            val emails = multi(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS, ids,
            )
            base.map { (id, key, name) -> ContactHit(id, key, name, phones[id].orEmpty(), emails[id].orEmpty()) }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun multi(uri: Uri, idCol: String, valueCol: String, ids: List<Long>): Map<Long, List<String>> {
        val out = LinkedHashMap<Long, MutableList<String>>()
        app.contentResolver.query(
            uri, arrayOf(idCol, valueCol),
            "$idCol IN (${ids.joinToString(",")})", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val v = c.getString(1)?.trim().orEmpty()
                if (v.isEmpty()) continue
                val list = out.getOrPut(c.getLong(0)) { ArrayList() }
                val d = ContactLogic.digits(v)
                if (list.none { it == v || (d.isNotEmpty() && ContactLogic.digits(it) == d) }) list += v
            }
        }
        return out
    }
}
