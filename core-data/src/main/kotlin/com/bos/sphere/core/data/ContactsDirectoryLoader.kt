package com.bos.sphere.core.data

import android.content.Context
import android.provider.ContactsContract

/**
 * STR-13: reads the optional contacts grant into a [ContactIndex] with public ContactsContract queries only. Never required:
 * without the grant (or on any failure) it returns [NoContacts]. Call from a background dispatcher; the result is an in-memory
 * index used for matching and is not stored anywhere.
 */
object ContactsDirectoryLoader {
    private const val MAX_ROWS = 40_000

    fun load(context: Context): ContactDirectory {
        val app = context.applicationContext
        if (!ContactSource(app).hasPermission()) return NoContacts
        return try {
            class Acc(val key: String, var display: String, var starred: Boolean) {
                val names = LinkedHashSet<String>(); val phones = LinkedHashSet<String>(); val emails = LinkedHashSet<String>()
                var given = ""; var family = ""
            }
            val byId = LinkedHashMap<Long, Acc>()
            val mimes = listOf(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            )
            app.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY, ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2, ContactsContract.Data.DATA3,
                ),
                "${ContactsContract.Data.MIMETYPE} IN (${mimes.joinToString(",") { "?" }})", mimes.toTypedArray(), null,
            )?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows++ < MAX_ROWS) {
                    val id = c.getLong(0)
                    val acc = byId.getOrPut(id) { Acc(c.getString(1) ?: "id$id", c.getString(2).orEmpty(), c.getInt(3) == 1) }
                    val v = c.getString(5)?.trim().orEmpty()
                    when (c.getString(4)) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            if (v.isNotEmpty()) acc.names += v
                            acc.given = c.getString(6).orEmpty(); acc.family = c.getString(7).orEmpty()
                            if (acc.given.isNotEmpty() && acc.family.isNotEmpty()) acc.names += "${acc.given} ${acc.family}"
                        }
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> if (v.isNotEmpty()) acc.names += v
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> if (v.isNotEmpty()) acc.phones += v
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> if (v.isNotEmpty()) acc.emails += v
                    }
                }
            }
            ContactIndex(byId.values.map { ContactIdentity(it.key, it.display, it.names + it.display, it.phones, it.emails, it.starred) })
        } catch (_: SecurityException) {
            NoContacts
        } catch (_: RuntimeException) {
            NoContacts
        }
    }

    /** The device owner's surname for prefilling the last-name setting, or null when the profile is unreadable or has a single name. */
    fun ownerLastName(context: Context): String? {
        val app = context.applicationContext
        if (!ContactSource(app).hasPermission()) return null
        return try {
            app.contentResolver.query(ContactsContract.Profile.CONTENT_URI, arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let(LastNames::suggestFor) else null }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
