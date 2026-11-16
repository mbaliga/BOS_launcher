package com.bos.sphere.ui

import com.bos.sphere.core.data.Condition
import com.bos.sphere.core.data.ConditionKind
import com.bos.sphere.core.data.GroupPriority
import com.bos.sphere.core.data.GroupRule
import com.bos.sphere.core.data.GroupedSection
import com.bos.sphere.core.data.GroupsConfig
import com.bos.sphere.core.data.AppPolicy
import com.bos.sphere.core.data.LaunchpadGroup
import com.bos.sphere.core.data.Section
import com.bos.sphere.core.design.L10n

/** STR-13: the words of the groups UI in the user's language (the core keeps an English-only summary for logs and tests). */
internal object GroupTexts {
    fun priorityLabel(p: GroupPriority) = when (p) {
        GroupPriority.Priority -> L10n.tr("Priority")
        GroupPriority.Normal -> L10n.tr("Normal")
        GroupPriority.Quiet -> L10n.tr("Quiet")
    }

    fun kindLabel(k: ConditionKind) = when (k) {
        ConditionKind.AppIs -> L10n.tr("App is")
        ConditionKind.PersonIs -> L10n.tr("Person is")
        ConditionKind.SenderContains -> L10n.tr("Sender name contains")
        ConditionKind.SenderStartsWith -> L10n.tr("Sender name starts with")
        ConditionKind.SenderEquals -> L10n.tr("Sender name is")
        ConditionKind.ConversationContains -> L10n.tr("Chat title contains")
        ConditionKind.TextContains -> L10n.tr("Text contains")
        ConditionKind.CategoryIs -> L10n.tr("Category is")
        ConditionKind.IsConversation -> L10n.tr("Is a conversation")
        ConditionKind.LastNameIsMine -> L10n.tr("Sender last name is my last name")
        ConditionKind.InContacts -> L10n.tr("Sender is in my contacts")
        ConditionKind.StarredContact -> L10n.tr("Sender is a starred contact")
        ConditionKind.TimeWindow -> L10n.tr("Time of day")
    }

    fun categoryLabel(c: String) = when (c) {
        "message" -> L10n.tr("Message")
        "call" -> L10n.tr("Call")
        "reminder" -> L10n.tr("Reminder")
        else -> c
    }

    /** The kinds whose value is free text typed by the user. */
    val textKinds = setOf(
        ConditionKind.SenderContains, ConditionKind.SenderStartsWith, ConditionKind.SenderEquals,
        ConditionKind.ConversationContains, ConditionKind.TextContains,
    )
    val categories = listOf("message", "call", "reminder")

    fun clause(c: Condition, appLabel: (String) -> String, personName: (String) -> String, myLastName: String): String {
        val v = c.value
        val last = myLastName.ifBlank { L10n.tr("(not set)") }
        return when (c.kind) {
            ConditionKind.AppIs -> L10n.tr(if (c.negate) "not in %s" else "in %s", appLabel(v))
            ConditionKind.PersonIs -> L10n.tr(if (c.negate) "who is not %s" else "who is %s", personName(v))
            ConditionKind.SenderContains -> L10n.tr(if (c.negate) "whose name does not contain \"%s\"" else "whose name contains \"%s\"", v)
            ConditionKind.SenderStartsWith -> L10n.tr(if (c.negate) "whose name does not start with \"%s\"" else "whose name starts with \"%s\"", v)
            ConditionKind.SenderEquals -> L10n.tr(if (c.negate) "not named \"%s\"" else "named \"%s\"", v)
            ConditionKind.ConversationContains -> L10n.tr(if (c.negate) "in chats whose title does not contain \"%s\"" else "in chats whose title contains \"%s\"", v)
            ConditionKind.TextContains -> L10n.tr(if (c.negate) "whose text does not contain \"%s\"" else "whose text contains \"%s\"", v)
            ConditionKind.CategoryIs -> L10n.tr(if (c.negate) "that is not a %s" else "that is a %s", categoryLabel(v))
            ConditionKind.IsConversation -> L10n.tr(if (c.negate) "outside a conversation" else "in a conversation")
            ConditionKind.LastNameIsMine -> L10n.tr(if (c.negate) "whose last name is not %s" else "whose last name is %s", last)
            ConditionKind.InContacts -> L10n.tr(if (c.negate) "who is not in my contacts" else "who is in my contacts")
            ConditionKind.StarredContact -> L10n.tr(if (c.negate) "who is not a starred contact" else "who is a starred contact")
            ConditionKind.TimeWindow -> L10n.tr(if (c.negate) "outside %s to %s" else "between %s and %s", AppPolicy.formatMinutes(c.from), AppPolicy.formatMinutes(c.to))
        }
    }

    private val senderKinds = setOf(
        ConditionKind.LastNameIsMine, ConditionKind.InContacts, ConditionKind.StarredContact, ConditionKind.PersonIs,
        ConditionKind.SenderContains, ConditionKind.SenderStartsWith, ConditionKind.SenderEquals,
    )

    fun targetLabel(g: LaunchpadGroup?): String =
        if (g == null) L10n.tr("(no group)")
        else if (g.priority == GroupPriority.Normal) g.name else L10n.tr("%s (%s)", g.name, priorityLabel(g.priority))

    /** "Anyone whose last name is Sharma → Family (Priority)", in the user's language. */
    fun summary(rule: GroupRule, cfg: GroupsConfig, appLabel: (String) -> String = { it }): String {
        val personName = { id: String -> cfg.person(id)?.name ?: id }
        val body = rule.conditions.map { clause(it, appLabel, personName, cfg.myLastName) }
            .reduceOrNull { a, b -> L10n.tr("%s and %s", a, b) }
        val subject = L10n.tr(if (rule.conditions.any { it.kind in senderKinds }) "Anyone" else "Any notification")
        val left = if (body == null) L10n.tr("%s (no conditions yet)", subject) else "$subject $body"
        return L10n.tr("%s → %s", left, targetLabel(cfg.group(rule.groupId)))
    }
}

/** The Launchpad column with groups: one [Section] per group section (for selection and keyboard focus), plus the grouped data. */
internal object GroupColumn {
    fun sections(grouped: List<GroupedSection>, fallbackTitle: String): List<Section> =
        grouped.map { gs -> Section(gs.group?.name ?: fallbackTitle, gs.key, gs.sections.flatMap { it.items }) }

    /** The by-app / by-conversation sub-headings inside a group section are shown only when there is more than one. */
    fun showSubHeads(gs: GroupedSection) = gs.sections.size > 1
}
